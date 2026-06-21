# 내가 받은 초대 리스트 조회 (Received Invitations Query)

작성일: 2026-06-21
브랜치: feat/team-formation

## 배경 / 목적

팀(2:2 매칭)에는 "내가 보낸 초대 현황"(`GET /teams/v1/invitation`)이 있으나, 초대받은 사람 관점의
"내가 받은 초대 리스트"가 없다. 초대받은 유저가 자신에게 온 (대기 중인) 초대들을 보고, 누가 초대했는지
확인할 수 있어야 한다.

## 범위

- `GET /teams/v1/received-invitations` 조회 엔드포인트 1개 추가 (인증 필요, path param 없음).
- 요청자가 **INVITED 구성원**인 **INVITING** 팀들을 **최신순(team id desc)** 으로 전체 반환.
- 각 항목 = 팀 메타(teamId·name·introduction) + **초대자(owner = ACTIVE 구성원) 프로필**.
- 페이지네이션 없음 (대기 중 초대는 소수, YAGNI).

비범위: FORMED/DEACTIVATED 팀, 보낸 초대(이미 별도 엔드포인트), 페이지네이션, 초대 수락/거절(별도 명령 엔드포인트 존재).

## 동작 정의

- **접근 제어**: 쿼리가 "요청자 = INVITED 구성원"으로 거르므로 내가 받은 초대만 노출된다. 별도 403 불필요.
- **초대자 식별**: INVITING 단계에서 owner는 ACTIVE, 초대 대상(나)은 INVITED. 따라서 같은 팀의 ACTIVE 구성원이 초대자다.
  (팀당 ACTIVE 구성원은 정확히 1명이므로 항목당 1행)
- **없을 때**: 빈 배열 `[]` 반환 (HTTP 200).
- 소프트 삭제(철회·해체)된 행은 `@SQLRestriction("deleted_at is null")`이 자동 제외.

## 응답 (항목별)

```
ReceivedInvitationResponse(
  teamId: Long,
  name: String,
  introduction: String?,
  inviter: Inviter,
)
Inviter(
  userId: Long,
  nickname: String,
  job: String?,
  companyName: String?,
  gender: Gender,
  profileImageCode: String,
  age: Int,
)
```

- 엔드포인트 응답 본문: `List<ReceivedInvitationResponse>`.
- `job`·`companyName`은 미입력 시 null. 나머지는 non-null.

## 구성 요소 (헥사고날 / CQRS query 슬라이스)

`SentInvitation` query 슬라이스 구조를 그대로 따른다. query는 자기 dao에만 의존하고 command 도메인·포트를 참조하지 않는다.

| 레이어 | 파일 | 역할 |
|---|---|---|
| core query dto | `ReceivedInvitation`, `ReceivedInvitationInviter` | read model (중첩, command 도메인 미참조) |
| core query port/in | `GetReceivedInvitationsUseCase` | `get(requesterId: Long): List<ReceivedInvitation>` |
| core query dao | `GetReceivedInvitationsDao` | `findInvited(requesterId: Long): List<ReceivedInvitation>` |
| core query service | `GetReceivedInvitationsService` | `@Transactional(readOnly = true)`, dao 위임 |
| infra query | `GetReceivedInvitationsDaoImpl` | QueryDSL 구현 |
| api | `ReceivedInvitationResponse` (+ nested `Inviter`) | 응답 DTO + `of(ReceivedInvitation)` |
| api | `TeamController.getReceivedInvitations()` | `GET /teams/v1/received-invitations` |

## DAO 쿼리 (단일 쿼리, 중첩 Projection) 및 인덱스

`GetReceivedInvitationsDaoImpl.findInvited(requesterId)`:

- base: `team_members me` — `me.userId = requesterId AND me.status = INVITED` (idx_user_id seek → status 잔여 필터, 유저당 INVITED 행 소수)
- join `teams team` on `team.id = me.teamId`, `team.status = INVITING`
- join `team_members owner` on `owner.teamId = team.id AND owner.status = ACTIVE` (= 초대자)
- join `match_user`(nickname·profileImageCode·age) + `user_details`(job·companyName) on `owner.userId`
- gender는 `team_members.gender`(owner) 사용
- `order by team.id desc`
- 투영: `Projections.constructor(ReceivedInvitation, team.id, team.name, team.introduction, Projections.constructor(ReceivedInvitationInviter, owner.userId, ownerMatch.nickname, ownerDetail.job, ownerDetail.companyName, owner.gender, ownerMatch.profileImageCode, ownerMatch.age))` (중첩 Projection)
- 팀당 owner 정확히 1명 → 항목당 1행 (dedup 불필요)

**인덱스: 신규 추가 불필요.** team_members `idx_user_id`(me)·`ux_team_id_user_id`(owner, team_id 프리픽스), match_user/user_details `ux_user_id`(eq_ref), teams PK. 풀스캔/filesort 없음(최종 정렬은 소수 행).

## 테스트

도메인 로직 추가가 없으므로(순수 조회) Kotest 유닛은 없음. api 경계 → E2E.

`GetReceivedInvitationsE2ETest` (`AbstractIntegrationSupport` + 픽스처 + `RestAssuredDsl`):

1. 초대받은 유저가 조회 → 팀 메타 + 초대자 프로필(닉네임·직업·회사명·성별·프로필이미지·나이) 포함 항목 반환.
2. 여러 건 → team id desc 최신순 정렬.
3. 제외: 내가 ACTIVE(=owner)인 팀, FORMED 팀, 미소속 팀.
4. 받은 초대 없음 → 빈 배열 (200).
5. 미인증 → 401.
