# 미팅(2:2) 팀 결성 완성 — 설계 (A 슬라이스)

> 작성일: 2026-06-20 · 브랜치: `feat/team-formation`

## 배경

미팅 기능은 1:1 솔로 매칭의 라이프사이클(초대→페어링→수락→성사→채팅)을 **팀 단위**로 미러링한다.
전체 파이프라인을 슬라이스로 나눠 순서대로 진행하며, 이 문서는 그 첫 조각인 **A. 팀 결성 완성**만 다룬다.

- **B. 팀 페어링**(FORMED 두 팀 → `team_match`, 배치 DAILY/신청 REQUIRED)
- **C. 미팅 수락·성사**(팀 단위 수락 → 양 팀 수락 시 코인 차감 + 4인 채팅방)
- **D. 조회**(내 팀/받은 미팅 목록)

B/C/D는 이 슬라이스 범위 밖이며 별도 spec→plan→구현 사이클로 진행한다.

### 이미 구현된 것 (main, commit `f17945a`)

- enum: `TeamStatus`(INVITING/FORMED/DEACTIVATED), `TeamMemberStatus`(INVITED/ACTIVE/DEACTIVE), `TeamMatchType`(DAILY/REQUIRED), `CoinUsageType.MEETING_INIT/ACCEPT`(40)
- 엔티티 4종: `TeamEntity`, `TeamMemberEntity`, `TeamMatchEntity`, `MatchedTeamEntity`
- 도메인 `Team.invite()` + 슬라이스 1개: 팀 초대/결성 `POST /teams/v1`
  - owner=ACTIVE, 초대대상=INVITED, team=INVITING 으로 생성
  - 검증: 자기초대 금지, 같은 성별만(`TEAM-001~004`)

> 참고: 초기 메모리에는 "초대=즉시 합류(auto-join)"로 적혀 있으나, 현재 코드는 초대 대상이 **INVITED 대기 상태**로 들어간다. 이 설계는 **수락해야 합류**하는 현재 코드 기준이다.

## 결정 사항 (브레인스토밍 합의)

| 항목 | 결정 |
|---|---|
| 초대받은 사람 합류 | **수락해야 합류** (INVITED→ACTIVE, 두 명 모두 ACTIVE면 팀 FORMED) |
| 초대 응답 | **수락·거절 둘 다** 가능 |
| 거절 시 팀 | **팀 해체**(DEACTIVATED) — owner가 새 팀을 다시 만들어 초대 |
| 한 사용자의 팀 개수 | **활성 1개만** (INVITING/FORMED 중복 불가) |
| A 슬라이스 동작 | 초대 수락 / 거절·취소(탈퇴) / 해체·떠나기 |
| API 형태 | 수락 + 탈퇴(거절·취소 통합) + 해체 = 3 엔드포인트 |
| 동시성 락 | A에 포함 (수락↔취소 경합 방지) |
| "한 팀만" 검증 | A에서 기존 invite에 추가 |
| 팀 조회 | **D 슬라이스로 미룸** (A 범위 밖) |

## 1. 상태 모델

```
INVITING ──(invited 수락)──> 두 명 모두 ACTIVE ──> FORMED
   │
   ├─(invited 거절 / owner 취소)──> DEACTIVATED + soft delete
   │
FORMED ──(구성원 해체·떠나기)──> DEACTIVATED + soft delete
```

- 비활성화는 솔로 `Match.delete(now)` 패턴을 따른다: **status=DEACTIVATED + deletedAt 세팅**을 팀(`TeamEntity`)·구성원(`TeamMemberEntity`) 양쪽에 적용(soft delete).
- `@SQLRestriction("deleted_at is null")` 덕분에 비활성화된 팀·구성원은 이후 조회에서 자동 제외된다.
- **"한 팀만" 제약 = 삭제 안 된 `team_members` 행의 존재 여부.** INVITED·ACTIVE 모두 사용자의 단일 슬롯을 점유하고, DEACTIVATED 팀의 구성원은 soft delete라 제외된다.

## 2. API

기존 `TeamController`(`/teams/v1`)에 추가한다. 모두 인증 필요. 응답은 기존 `TeamResponse` 재사용(상태 변화 반영).

| 메서드 | 경로 | 의미 | 허용자 / 상태 |
|---|---|---|---|
| POST | `/teams/v1` (기존) | 팀 초대·결성 | — (한 팀만 검증 **추가**) |
| POST | `/teams/v1/{teamId}/acceptance` | 초대 수락 | INVITED 당사자, INVITING |
| DELETE | `/teams/v1/{teamId}/invitation` | 거절·초대취소 | 구성원, INVITING |
| DELETE | `/teams/v1/{teamId}` | 해체·떠나기 | 구성원, FORMED |

- `DELETE …/invitation`: 호출자가 invited면 "거절", owner면 "초대취소". 서버가 역할로 구분하되 결과는 동일(DEACTIVATED). 도메인 메서드 `withdrawInvitation`이 INVITING 상태와 구성원 여부만 검증.
- `DELETE …/{teamId}`: FORMED 팀의 구성원이 떠나면 2인 팀이 유지될 수 없으므로 팀 전체를 해체(DEACTIVATED). 도메인 메서드 `disband`.

## 3. 도메인 변경

### `Team` (core/match/command/domain/Team.kt) — 인스턴스 행위 추가

- `acceptInvitation(userId): Team`
  - `validateAcceptable(userId)`: 상태가 INVITING이 아니면 `INVALID_TEAM_STATUS`, 해당 userId 멤버가 없으면 `NOT_TEAM_MEMBER`, 그 멤버가 INVITED가 아니면 `NOT_INVITED_MEMBER`.
  - 멤버를 ACTIVE로 전환하고, 전원 ACTIVE면 status=FORMED로 전이한 새 `Team` 반환.
- `withdrawInvitation(userId, now): Team`
  - `validateWithdrawable(userId)`: 상태가 INVITING이 아니면 `INVALID_TEAM_STATUS`, 구성원이 아니면 `NOT_TEAM_MEMBER`.
  - status=DEACTIVATED, deletedAt=now, 구성원 전원 DEACTIVE+deletedAt(soft delete)한 새 `Team` 반환.
- `disband(userId, now): Team`
  - `validateDisbandable(userId)`: 상태가 FORMED가 아니면 `INVALID_TEAM_STATUS`, 구성원이 아니면 `NOT_TEAM_MEMBER`.
  - withdraw와 동일한 비활성화(내부 공통 `deactivate(now)` 재사용).

> 검증은 서비스에 `if…throw`를 나열하지 않고 도메인의 `validate…` 함수로 캡슐화한다(CLAUDE.md 규칙). `now`는 파라미터 주입.

### `TeamMembers` (core/match/command/domain/TeamMembers.kt) — 컬렉션 행위 추가

- `find(userId): TeamMember?`
- `accept(userId): TeamMembers` — 해당 멤버만 ACTIVE로 교체
- `allActive(): Boolean` — 전원 ACTIVE 여부
- `deactivateAll(now): TeamMembers` — 전원 DEACTIVE + deletedAt

(`TeamMember`는 `copy`로 상태·deletedAt 변경. 기존 `isMember`/`userIds` 유지)

## 4. 포트 · 어댑터 · 서비스

### out-port (신규) `GetTeamPort` — core/match/command/application/port/out

- `findById(teamId: Long): Team?` — 헤더+구성원을 도메인으로 로드
- `existsActiveTeamMember(userId: Long): Boolean` — 삭제 안 된 team_member 존재 여부

(CQRS상 Save/Get 포트를 분리. `SaveTeamPort.save`는 기존 그대로 사용)

### 어댑터 `TeamAdapter` (infra) — `GetTeamPort`도 구현

- 엔티티당 어댑터 하나 원칙에 따라 `TeamAdapter`가 `SaveTeamPort` + `GetTeamPort`를 함께 구현.
- `findById`: 헤더 1건 + `team_members`(by teamId) 로드 → `toDomain`.
- `existsActiveTeamMember`: `teamMemberJpaRepository.existsByUserId(userId)` (Spring Data 파생 쿼리, `@SQLRestriction`이 삭제행 제외).
- **save가 update를 지원**하도록 `TeamMapper`/`TeamMemberMapper`의 `toEntity`가 **id·status·deletedAt를 왕복**하는지 점검·보강(현재는 신규 INSERT 경로만 검증됨). 상태 전환·soft delete가 UPDATE로 영속화돼야 한다.

### in-port + 서비스 (신규 3개, 각 `@Transactional`)

- `AcceptTeamInvitationUseCase` / `AcceptTeamInvitationService`
- `WithdrawTeamInvitationUseCase` / `WithdrawTeamInvitationService`
- `DisbandTeamUseCase` / `DisbandTeamService`

공통 흐름: `getTeamPort.findById` (없으면 `TEAM_NOT_FOUND`) → 도메인 메서드 호출 → `saveTeamPort.save`. `now`는 서비스에서 `LocalDateTime.now()`로 주입.

### 기존 `InviteTeamService` 보강

- `GetTeamPort` 주입. invite 시 owner·invitedUserId 각각 `existsActiveTeamMember`가 true면 `ALREADY_IN_TEAM`.

## 5. 동시성

수락(invited)과 초대취소(owner)가 동시에 들어오면 둘 다 INVITING을 읽고 한쪽은 FORMED, 다른쪽은 DEACTIVATED로 last-write-wins가 날 수 있다. 솔로 `SendInterestService`의 패턴을 따른다:

- 수락/탈퇴/해체 서비스에 `@DistributedLock(prefix = LockKeyConstraints.TEAM_LIFECYCLE, keys = ["#teamId"], waitTime = 0)`.
- 경합 대상이 "팀"이므로 키는 userId가 아니라 **teamId**. waitTime=0이라 동시 요청 시 한쪽은 즉시 409.
- `LockKeyConstraints`에 `TEAM_LIFECYCLE` 프리픽스 추가.

## 6. 에러코드 (`TeamErrorCode` 추가)

| 코드 | 이름 | 상태 |
|---|---|---|
| TEAM-005 | `TEAM_NOT_FOUND` | 404 |
| TEAM-006 | `NOT_TEAM_MEMBER` | 403 |
| TEAM-007 | `NOT_INVITED_MEMBER` | 400 |
| TEAM-008 | `INVALID_TEAM_STATUS` | 409 |
| TEAM-009 | `ALREADY_IN_TEAM` | 409 |

## 7. 테스트

- **도메인 유닛(Kotest)** — `TeamTest`에 추가:
  - `acceptInvitation`: 정상(→FORMED), 비INVITED 멤버 수락 실패, 비구성원 실패, 잘못된 상태 실패.
  - `withdrawInvitation`/`disband`: 정상 비활성화, 상태/구성원 검증 실패.
  - `TeamMembers`: `accept`/`allActive`/`deactivateAll`/`find`.
- **E2E(`meeple-api`, `AbstractIntegrationSupport` + `TeamFixture`)**:
  - 초대 수락 → 팀 FORMED.
  - 거절 → DEACTIVATED. owner 초대취소 → DEACTIVATED.
  - FORMED 팀 해체 → DEACTIVATED.
  - 초대 시 "한 팀만" 위반(owner/초대대상이 이미 활성 팀) → `ALREADY_IN_TEAM`.
  - 권한/상태 위반(비구성원·잘못된 상태) → 해당 에러코드.

## 범위 밖 (명시)

- 팀/팀매칭 **조회 엔드포인트** (D 슬라이스)
- 팀 **페어링**(team_match 생성), 미팅 **수락·성사**, **코인** 차감·환불, 4인 **채팅방** (B/C 슬라이스)
- 재초대(거절·취소 후 같은 팀 재사용) — 해체 후 새 팀 생성으로 대체하므로 미구현
