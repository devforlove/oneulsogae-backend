# 내가 보낸 초대 현황 조회 (Sent Invitation Query)

작성일: 2026-06-21
브랜치: feat/team-formation

## 배경 / 목적

2:2 팀 매칭의 팀 엔드포인트(`/teams/v1`)에는 현재 명령(초대·수락·철회·해체)만 있고,
초대자가 **자신이 보낸 초대의 현황**을 조회할 수단이 없다.

"초대 화면"에서 초대자(owner)가 자신이 만든 팀(초대 대기 중)의 현황을 본다.
응답은 **초대를 보낸 유저에게만** 노출되어야 한다.

## 범위

- `GET /teams/v1/invitation` 단일 조회 엔드포인트 1개 추가 (path param 없음).
- 요청자가 **ACTIVE 구성원(=초대자)** 인 `INVITING` 팀 중 **가장 최근(team id desc) 1건**을 반환.
- 한 사용자는 활성 팀(INVITING/FORMED)에 하나만 속할 수 있어(invite 명령이 `ALREADY_IN_TEAM`으로 차단)
  실질적으로 단건이지만, 안전하게 "가장 최근 INVITING 1건"으로 정의한다.

비범위: FORMED 팀 조회, 초대받은 사람 관점 조회, 프로필(닉네임·직업 등) 조인, 페이지네이션.

## 동작 정의

- **접근 제어**: 초대받은 사람은 `INVITED` 상태이고 비구성원은 행이 없어, "ACTIVE 구성원인 INVITING 팀"
  쿼리 조건만으로 "초대를 보낸 유저에게만" 이 충족된다. 별도 403 처리 불필요.
- **없을 때**: `null` 반환 → `ApiResponse.success(null)` (HTTP 200). 진행 중인 초대가 없는 정상 상태로 본다.
  (404 아님 — 에러가 아니라 "현황 없음")

## 응답 (최소 정보)

프로필 조인 없음. 팀 메타 + 구성원의 userId·수락 상태만.

```
SentInvitationResponse(
  teamId: Long,
  name: String,
  introduction: String?,
  status: TeamStatus,          // INVITING
  members: List<Member>,       // Member(userId: Long, status: TeamMemberStatus)
)
```

- `members`의 status: `ACTIVE` = 초대자, `INVITED` = 수락 대기 중인 초대 대상.

## 구성 요소 (헥사고날 / CQRS query 슬라이스)

`SearchInvitableUsers` query 슬라이스 구조를 그대로 따른다. query는 자기 dao에만 의존하고 command 도메인·포트를 참조하지 않는다.

| 레이어 | 파일 | 역할 |
|---|---|---|
| core query dto | `SentInvitation`, `SentInvitationMember` | read model (command 도메인 미참조) |
| core query port/in | `GetSentInvitationUseCase` | `get(requesterId: Long): SentInvitation?` |
| core query dao | `GetSentInvitationDao` | `findLatestInviting(requesterId: Long): SentInvitation?` |
| core query service | `GetSentInvitationService` | `@Transactional(readOnly = true)`, dao 위임 |
| infra query | `GetSentInvitationDaoImpl` | QueryDSL 구현 |
| api | `SentInvitationResponse` | 응답 DTO + `of(SentInvitation)` |
| api | `TeamController.getSentInvitation()` | `GET /teams/v1/invitation` |

## DAO 쿼리 (2-step) 및 인덱스

`GetSentInvitationDaoImpl.findLatestInviting(requesterId)`:

1. **owner의 최근 INVITING 팀 헤더 조회**
   - `team_members`에서 `user_id = requesterId AND status = ACTIVE` (idx_user_id seek → 유저당 팀 행 극소수, status 필터)
   - `teams` PK 조인, `teams.status = INVITING`
   - `order by teams.id desc`, `limit 1`
   - 없으면 `null` 반환(종료)
2. **그 팀의 구성원 조회**
   - `team_members where team_id = ?` (ux_team_id_user_id의 team_id 프리픽스 seek)
   - userId·status 투영 → `SentInvitationMember` 리스트
3. 1·2를 `SentInvitation`으로 조립해 반환.

**인덱스: 신규 추가 불필요.** 기존 `idx_user_id`, `ux_team_id_user_id`로 seek 가능. 풀스캔/filesort 없음.

## 테스트

도메인 로직 추가가 없으므로(순수 조회) Kotest 유닛은 없음. api 경계 → E2E.

`GetSentInvitationE2ETest` (`AbstractIntegrationSupport` + 엔티티 픽스처 + `RestAssuredDsl`):

1. 초대자가 자신이 보낸 INVITING 초대를 조회 → teamId/name/introduction/status + 두 구성원(자신 ACTIVE, 대상 INVITED) 반환.
2. 초대받은 유저가 조회 → null (200).
3. 비구성원이 조회 → null (200).
4. 초대 철회(withdraw) 후 초대자가 조회 → null (200).
```
