# 추천 팀 배치: 유저별 재매칭 팀 제외 설계 (접근 B)

## 배경 / 요구사항

`RecommendedTeamBatchService`는 솔로 유저마다 근접·반대 성별 `ACTIVE` 팀 1개를 골라
`recommended_teams`(유저당 1행 upsert)에 기록한다. 현재 후보 필터는 `team.status = ACTIVE`뿐이라
과거 매칭 이력을 거르지 않는다.

요구사항: **솔로 유저 U에게 상대 팀을 추천할 때, U가 과거에 (자기 팀을 꾸려) MATCHED됐던 상대 팀은 추천 후보에서 제외한다.**
(per-user 재매칭 방지)

확정된 결정:
- 제외 기준은 **"성사(MATCHED)된 적 있음"**. 제외 단위는 **팀 식별자(team_id) 기준**.
- 접근은 **B: 성사 시점에 전용 이력 테이블에 기록**. (조회 최적화)
- **백필 없음** — 테이블 도입 이후의 매칭만 추적한다.
- 쓰기는 이벤트 핸들러(AFTER_COMMIT best-effort)가 아니라 **성사 처리와 같은 트랜잭션**에 둔다.

## 접근 A를 버린 이유

A는 신규 스키마 없이 소프트 삭제된 `team_members·matched_teams·team_matches`를 네이티브 SQL로 우회 조인하고,
`expires_at` 연장 부수효과로 성사를 판정했다. 그러나:
- 조회가 **3-테이블 소프트삭제 조인 + 전체 유저풀 IN**이라 배치 부하가 크다.
- `expires_at > introduced_date + 50년`이라는 **암묵 휴리스틱**에 의존(core 상수 변경 시 조용히 깨짐).
- `@SQLRestriction` 우회를 위한 **네이티브 SQL**(컨벤션 예외).

B는 성사 시점에 명시적으로 기록해 두므로, 조회가 **단일 테이블 인덱스 seek**로 끝나고 휴리스틱·우회·네이티브가 모두 사라진다.

## 설계

### 1. 이력 테이블 `recommended_team_histories`

| 컬럼 | 의미 |
|---|---|
| `id` | PK |
| `user_id` | 매칭한 유저 |
| `team_id` | 그 유저가 **매칭한 상대 팀** |
| `created_at/updated_at/deleted_at` | BaseEntity (소프트 삭제 안 함 — 항상 null) |

- `UNIQUE(user_id, team_id)` — 멱등 보장 + 조회 `WHERE user_id = ?`를 선두 컬럼으로 seek.
- 테스트 스키마는 `ddl-auto: create-drop`이라 엔티티에서 자동 생성된다. `docs/migration/recommended_team_histories.sql`은 운영 DDL 참고용.

### 2. 쓰기 (성사 시점, 같은 트랜잭션)

성사는 `SendTeamInterestService.sendInterest()` → `completeMatch()`에서 일어나며, 이 지점에 이미 양 팀(`Teams`)과
각 팀의 ACTIVE 구성원이 로드돼 있다. 여기서 새 out-port를 호출한다.

- core 도메인 캡슐화: `Teams.matchHistories(): List<RecommendedTeamHistory>` — 각 팀의 ACTIVE 구성원마다
  `(userId → 상대 팀 id)`를 만든다(2:2 → 4건). 서비스는 결과만 포트에 넘긴다.
- out-port: `SaveRecommendedTeamHistoryPort.saveAll(histories)`. 어댑터는 `existsByUserIdAndTeamId`로
  기존 행을 건너뛴 뒤 저장(멱등).
- **같은 트랜잭션**에 두는 이유: 이력 누락은 "매칭한 상대를 다시 추천"이라 정합성이 중요. 성사·코인·채팅방과
  원자적으로 기록한다(이미 분산 락 `TEAM_MATCH_INTEREST`로 보호됨). AFTER_COMMIT 이벤트는 best-effort라 부적합.

#### 멱등·중복 키 충돌 주의

같은 유저가 같은 상대 팀과 두 번 매칭하는 경우(U가 팀 X1으로 Y와 매칭 → X1 해체 → U가 X2로 다시 Y와 매칭)
`(user_id, team_id)`가 중복된다. 어댑터가 `exists` 체크로 건너뛰지 않으면 **두 번째 성사 트랜잭션이 유니크 위반으로 롤백**된다.
한 유저는 동시에 한 팀에만 ACTIVE라 동시 삽입은 불가능하므로, 순차 `exists`-후-`save`로 안전하다.

### 3. 조회·제외 (배치)

- scheduler `GetRecommendedTeamHistoryDao.findMatchedTeamIds(userId): Set<Long>` — 유저별 단건 seek.
  infra 구현은 `RecommendedTeamHistoryJpaRepository.findByUserId(userId)`(Spring Data 파생 쿼리)로 team_id 투영.
- `RecommendedTeamBatchService`: 루프에서 대상마다 `findMatchedTeamIds(target.userId)`로 제외 집합을 얻어
  `findNearestRandomTeam`의 각 권역 후보에 `filterNot`. **전체 풀 IN을 쓰지 않는다.**

## 동작이 실제로 발생하는 경우

U의 팀 X가 해체되어 U는 솔로(추천 대상)가 됐지만 상대 Y는 해체하지 않아 여전히 `ACTIVE` → Y는 후보 풀에 남아
U에게 다시 추천될 수 있다 → 이 제외가 막는다. (테이블에 (U, Y) 행이 성사 시점에 기록돼 있으므로)

## 테스트

- **core 유닛**(`TeamsTest`): `matchHistories()`가 각 구성원→상대 팀을 만들고 INVITED를 제외함.
- **infra E2E**(`RecommendedTeamHistoryAdapterE2ETest`): `saveAll` 저장 + 재호출 시 중복 `(user_id, team_id)` 건너뜀(멱등).
- **성사 플로우 E2E**(`SendTeamInterestE2ETest` MATCHED 케이스 확장): 성사 후 4인 각자 → 상대 팀 이력 4행 기록 확인.
- **배치 유닛**(`RecommendedTeamBatchServiceTest`): 가짜 dao가 U→{Y} 반환 시 U에게 Y 미추천·다른 후보 선택, 전부 제외 시 skipped.

## 범위 밖

- 백필(기존 성사 데이터) 없음.
- 기존 `recommended_teams` 행 정리 없음(유저당 upsert라 다음 실행 때 갱신).
- 전역적 "성사된 팀은 누구에게도 추천 안 함"은 범위 아님.
