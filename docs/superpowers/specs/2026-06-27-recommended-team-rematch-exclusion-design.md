# 추천 팀 배치: 유저별 재매칭 팀 제외 설계

## 배경 / 요구사항

`RecommendedTeamBatchService`는 솔로 유저마다 근접·반대 성별 `ACTIVE` 팀 1개를 골라
`recommended_teams`(유저당 1행 upsert)에 기록한다. 현재 후보 필터는 `team.status = ACTIVE`뿐이라
**과거 매칭/추천 이력을 전혀 거르지 않는다.**

요구사항: **솔로 유저 U에게 상대 팀을 추천할 때, U가 과거에 (자기 팀을 꾸려) MATCHED됐던 상대 팀은 추천 후보에서 제외한다.**
(per-user 재매칭 방지)

확정된 결정:
- 제외 기준은 **"성사(MATCHED)된 적 있음"** — 단순 소개/제안만 된 상대는 제외하지 않는다.
- 제외 단위는 **팀 식별자(team_id) 기준** — 상대 팀의 구성원이 일부 바뀌어 새 팀(team_id)을 만들면 다른 팀으로 보고 추천 가능.
- 접근은 **A: 기존 데이터에서 파생** (신규 스키마/마이그레이션 없음).

## 데이터 현실

U마다 ① U의 과거 소속 팀 → ② 그 팀들이 MATCHED까지 갔던 매칭 → ③ 그 상대 팀을 구해야 한다.
기존 데이터로 복원 가능하나 두 가지 제약이 있다.

1. **소프트 삭제 우회 필요** — U는 현재 솔로이므로 U의 과거 `team_members` 행은 `deleted_at`이 찍혀 있고,
   `matched_teams`도 종료 시 soft delete된다. 두 테이블 모두 `@SQLRestriction("deleted_at is null")`이
   엔티티에 박혀 있어, 과거 이력을 읽으려면 **네이티브 SQL로 우회**해야 한다.
2. **"MATCHED였음"을 명시하는 컬럼이 없음** — 종료되면 `matched_teams.status`는 `DEACTIVE`로 덮어써지고
   (미성사 해체와 구분 불가), `team_matches.status`는 `CLOSED`(미성사 만료와 구분 불가)가 된다.
   유일한 영속 흔적은 **성사 시 `expires_at`을 +100년 연장(`MATCHED_EXPIRATION_EXTENSION_YEARS = 100`)하고
   종료(`delete()`)해도 되돌리지 않는다**는 부수효과다.

### 성사 판정 신호 (보강)

`expires_at > now + 99년` 같은 "현재 시각 기준" 비교는 성사 후 약 1년이 지나면 깨진다
(`expires_at ≈ 소개일 + 100년`이 `now + 99년`보다 작아지기 때문). 대신 **각 매칭의 자기 `introduced_date` 기준**으로
비교하면 배치 실행 시점과 무관하게 영구히 정확하다.

```
t.expires_at > t.introduced_date + INTERVAL 50 YEAR
```

- 정상 만료는 `소개일 + 며칠` 수준, 성사는 `+100년` → 50년이 안전한 분리점.
- 이 임계값은 core `TeamMatch.MATCHED_EXPIRATION_EXTENSION_YEARS = 100`에 묶여 있다(그 값이 바뀌면 함께 재검토).

이 신호가 필요한 이유: U의 과거 팀 X는 해체되며 `matched_teams.status`가 `DEACTIVE`로 덮어써져
상태 컬럼만으로는 "성사했었다"를 알 수 없다. 매칭 헤더의 `expires_at`만이 유일한 영속 흔적이다.

## 설계

### 1. 신규 조회 DAO (scheduler `match/query/dao`)

```kotlin
interface GetUserMatchHistoryDao {
    /** 주어진 유저들이 과거(소속했던 팀 기준) MATCHED됐던 상대 team_id 집합. 유저별로 묶어 반환. */
    fun findPreviouslyMatchedTeamIdsByUser(userIds: Set<Long>): PreviouslyMatchedTeams
}
```

read model `PreviouslyMatchedTeams` (scheduler `match/query/dto`) — 일급 컬렉션:

```kotlin
class PreviouslyMatchedTeams(private val byUser: Map<Long, Set<Long>>) {
    fun opponentTeamIdsOf(userId: Long): Set<Long> = byUser[userId] ?: emptySet()
}
```

### 2. 구현 (infra `match/query`) — `GetUserMatchHistoryDaoImpl`, 네이티브 SQL

```sql
SELECT tmself.user_id, mt_opp.team_id
FROM team_members tmself
JOIN matched_teams mt_self ON mt_self.team_id = tmself.team_id
JOIN team_matches t       ON t.id = mt_self.team_match_id
JOIN matched_teams mt_opp ON mt_opp.team_match_id = t.id AND mt_opp.team_id <> tmself.team_id
WHERE tmself.user_id IN (:userIds)
  AND t.expires_at > t.introduced_date + INTERVAL 50 YEAR
```

- **네이티브 SQL** — 세 테이블 모두 soft-delete 대상이므로 과거 이력을 읽으려면 `deleted_at` 조건을 걸지 않아야 한다.
  `@SQLRestriction`은 QueryDSL/JPQL로는 우회 불가하므로 `EntityManager.createNativeQuery`를 주입해 구현한다.
  (프로젝트 쿼리 우선순위 ①Spring Data→②QueryDSL→③JPQL의 예외 — soft-delete 우회가 강제하는 불가피한 선택)
- 결과(`user_id`, `team_id`)를 `Map<Long, MutableSet<Long>>`으로 묶어 `PreviouslyMatchedTeams`로 감싼다.
  네이티브 결과는 `Array<Any>`이므로 `(row[0] as Number).toLong()` 식으로 캐스팅한다.
- `userIds`가 비면 쿼리를 생략하고 빈 결과를 반환한다(`IN ()` 방지).

#### 인덱스 효율

`team_members.idx_user_id`(IN seek) → `matched_teams.idx_team_id` → `team_matches` PK →
`matched_teams.ux_team_match_id_team_id`(선두 `team_match_id`) 순으로 받쳐진다. 풀스캔/filesort 없음.

### 3. 배치 통합 (`RecommendedTeamBatchService`)

- 생성자에 `getUserMatchHistoryDao: GetUserMatchHistoryDao` 추가.
- `targets` 적재 직후 한 번에 이력 맵 적재:
  ```kotlin
  val previouslyMatched: PreviouslyMatchedTeams =
      getUserMatchHistoryDao.findPreviouslyMatchedTeamIdsByUser(targets.map { it.userId }.toSet())
  ```
- `findNearestRandomTeam`에 제외 집합 인자를 추가:
  ```kotlin
  val teamId: Long? = findNearestRandomTeam(target, pool, previouslyMatched.opponentTeamIdsOf(target.userId))
  ...
  private fun findNearestRandomTeam(target: RecommendableSoloUser, pool: TeamPool, excludedTeamIds: Set<Long>): Long? {
      val teamGender: Gender = target.gender.opposite()
      val regionOrder: List<Long> = regionShuffler.shuffleNearest(regionProximityPort.nearbyRegionIds(target.regionId))
      for (regionId: Long in regionOrder) {
          val teamIds: List<Long> = pool.teamIdsOf(teamGender, regionId).filterNot { it in excludedTeamIds }
          if (teamIds.isNotEmpty()) return teamIds.random(random)
      }
      return null
  }
  ```
- 클래스 주석의 "이전 매칭/추천 이력은 필터링하지 않는다" 문구를 실제 동작에 맞게 갱신.

## 동작이 실제로 발생하는 경우

U의 팀 X가 해체되어 U는 솔로(추천 대상)가 됐지만, **상대 Y는 해체하지 않아 여전히 `ACTIVE`** →
Y는 후보 풀에 남아 U에게 다시 추천될 수 있다 → **이 제외가 막는다.**
(양쪽 다 해체된 상대는 `DEACTIVATED`라 후보 풀에 없어 자연 제외된다.)

U가 솔로(추천 대상)라는 것은 U의 과거 팀이 종료됐음을 의미하므로, U의 과거 소속 팀 행은 항상 soft-delete 상태다 →
네이티브 우회가 필수다.

## 테스트

- **scheduler 유닛 테스트** (`RecommendedTeamBatchService`):
  가짜 `GetUserMatchHistoryDao`가 U→{Y}를 반환할 때, U에게 Y가 추천되지 않고 같은 권역의 다른 후보가 선택됨을 검증.
  제외 후 후보가 없으면 `skipped` 처리됨을 검증. 기존 테스트가 있으면 새 의존성 추가에 맞춰 갱신.
- **infra 통합 테스트** (네이티브 쿼리 — 접근 A의 취약점이라 실DB 검증 필수):
  - soft-delete된 과거 팀 X(MATCHED→CLOSED, `expires_at` 연장됨)의 상대 Y가 반환됨.
  - **미성사로 CLOSED된 매칭(`expires_at` 미연장)의 상대는 반환되지 않음.**
  - 현재 진행 중인 MATCHED(상대 Y가 여전히 ACTIVE)의 상대 Y가 반환됨.
  - ※ infra 모듈에 DAO 통합 테스트 골격(Testcontainers 등)이 있는지는 플랜 단계에서 확인한다.

## 범위 밖 (명시)

- 배치의 **추천 기록 시점 제외**까지만 한다. 이미 `recommended_teams`에 있던 행은 손대지 않는다
  (유저당 upsert라 다음 실행 때 자연 갱신).
- 전역적 "성사된 팀은 누구에게도 추천 안 함"은 이번 범위가 아니다(별도 요구사항).
