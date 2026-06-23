# 거리 기반 1:1 매칭 재작성 설계

작성일: 2026-06-23
브랜치: `feat/recommended-team` 기준(별도 작업 브랜치 분기 예정)

## 1. 배경 / 목표

현재 1:1 매칭은 두 경로로 동작한다.

- **일일 배치**(`RunDailyMatchBatchService` / `MatchIntroducer`, meeple-scheduler): 전체 활성 유저를 돌며 각자에게 1명씩 소개.
- **온보딩 즉시 추천**(`RecommendMatchService`, meeple-core): 가입/온보딩 직후 그 유저에게 1명 즉시 추천.

두 경로 모두 **`regionCode`(시도 기반 1~5 광역코드) 정확 일치 + 풀 내 랜덤(SPOP)** 으로 후보를 고른다. `RegionEntity`에 지역 대표 위경도(lat/long)가 있으나 매칭엔 쓰이지 않는다.

**목표**

1. 후보 선택을 **"같은 regionCode + 랜덤" → "가까운 region 순서"**(지리적 거리)로 교체. 배치·온보딩 둘 다.
2. **한 번이라도 소개된 쌍(`solo_matches`에 레코드 존재)은 재소개하지 않는다.** 배치는 현행 `existsByPair` 유지, **온보딩 경로에는 이력 제외를 신규 추가**(현재 온보딩은 이력 체크가 없음).
3. 1:1 매칭 기준을 **`regionId`로 통일**. 레거시 `regionCode`는 **팀(2:2) 매칭 전용으로만 잔존**시키고 1:1 경로에서는 제거.

**비목표(이번 범위 밖)**

- 팀(2:2) 매칭 변경. 팀은 의도적으로 거친 `regionCode`로 후보 폭을 넓히므로 `regionId` 이관은 동작이 바뀌는 별개 과제다. `match_user.region_code`·`UserDetail.regionCode`·`Region.resolveAreaCode`·`MatchProfileSnapshot.regionCode`는 **그대로 보존**한다.

## 2. 핵심 설계 결정 (확정된 전제)

| 항목 | 결정 |
|---|---|
| "가까움"의 기준 | **지리적 거리**(지역 대표 위경도, haversine). 유저는 개인 좌표가 없고 `regionId`만 있으므로 거리는 "지역 간 거리"로 수렴. 같은 지역 = 거리 0. |
| 거리 계산 방식 | **지역 근접 순서 사전계산**(Redis GEO 아님). 시군구 ~250개로 적어 지역 간 거리·정렬을 한 번 계산해 캐시. |
| "매칭된 적 있음"의 정의 | `solo_matches`에 그 쌍의 레코드가 있으면(상태 무관, 즉 PROPOSED 이상) 제외. 현행 `existsByPair`/`member_key` 유지. |
| `regionCode` 제거 범위 | **1:1 경로에서만** 제거. 팀이 읽는 `match_user.region_code`/`UserDetail.regionCode`는 유지. |

## 3. 신규 핵심 컴포넌트: `RegionProximity`

지역 좌표로부터 "각 지역 → 가까운 지역 순서"를 만들어 캐시한다.

### 3.1 순수 계산기 (도메인 로직, Kotest 유닛 테스트 대상)

- 입력: 전체 지역의 `(regionId, latitude, longitude)` 목록.
- 출력: `Map<Long, List<Long>>` — 각 `regionId`에 대해 **가까운 순으로 정렬된 전체 regionId 리스트**. 자기 지역이 거리 0으로 맨 앞.
- 거리: haversine(위경도 → 거리). 정렬은 거리 오름차순, 거리 동률은 `regionId` 오름차순으로 안정적 타이브레이크.
- 프레임워크/인프라 비의존(순수 Kotlin). `now`/외부 의존 없음 → Kotest 유닛으로 검증. 위치는 `infra/region`에 레지스트리와 함께 두되 **Spring 비의존 순수 클래스**로 작성(유닛 테스트 가능). (region proximity는 핵심 비즈니스 도메인이 아니라 좌표 파생 reference 로직이므로 core 도메인이 아닌 infra에 둔다.)
- 250×250 ≈ 62,500 long 보관 — 메모리에서 사소함.

### 3.2 인프라 레지스트리 (`@Component`, `infra/region`)

- 앱 기동 시 `RegionJpaRepository.findAll()`로 좌표를 읽어 계산기를 **한 번 빌드해 메모리 캐시**.
- 지역은 정적 시드 데이터(~250행)이므로 **런타임 무효화 없음**(재기동 시 재빌드). 이 단순화를 의도적으로 채택한다.
- 조회 API: `nearbyRegionIds(regionId: Long): List<Long>`(가까운 순). 두 경로가 공유.

## 4. 온보딩 경로 (core → infra)

- `RecommendMatchService`: `MatchUser.regionCode` 대신 **`MatchUser.regionId`**를 후보 포트에 전달. 서비스는 거리 메커니즘을 모른다(헥사고날 경계 유지).
- out-port 시그니처 변경:
  ```kotlin
  // 변경 전: fun findOneCandidate(gender: Gender, regionCode: Int, loginAfter: LocalDateTime): Long?
  fun findOneCandidate(
      requesterId: Long,
      gender: Gender,        // 상대(반대) 성별
      regionId: Long,        // requester의 지역
      loginAfter: LocalDateTime,
  ): Long?
  ```
  - `requesterId` 추가: 어댑터가 **이력(이미 매칭된 상대) 제외**에 필요.
- 어댑터(`MatchUserAdapter`):
  1. 레지스트리에서 `regionId`의 **근접 지역 순서**를 얻는다.
  2. requester와 이미 `solo_match`를 공유하는 상대 `user_id` 집합을 **한 번 조회**해 제외 대상으로 둔다.
  3. **가까운 지역부터 순회**하며 "상대 성별 + `lastLoginAt >= loginAfter` + 이력 없음" 후보를 찾고, 첫 번째를 반환. 같은 지역 내 동순위는 **최근 로그인 우선**. 첫 후보 발견 시 즉시 종료.
  4. 근접 지역을 모두 소진해도 없으면 `null`.

## 5. 일일 배치 경로 (scheduler → infra)

- Redis Set 풀 구조 유지, **키를 `regionId`로**: `match:pool:{gender}:{regionId}`.
- 신규 scheduler out-port:
  ```kotlin
  interface RegionProximityPort {
      fun nearbyRegionIds(regionId: Long): List<Long>  // 가까운 순
  }
  ```
  infra 어댑터가 §3.2 레지스트리로 구현.
- `MatchIntroducer`: 기존 "같은 지역(Tier1) → 성별 폴백(Tier2)" 2단계를 **"근접 지역 순서대로 풀 pop"**으로 교체.
  - 가까운 지역부터 `pop(partnerGender, regionId)`(SPOP, 동순위 랜덤 타이브레이크) → `existsByPair` 통과 시 채택, 아니면 push-back 후 다음 후보/다음 지역.
  - 지역당 history 충돌 재시도 상한 `MAX_CANDIDATE_ATTEMPTS` 유지.
  - 모든 근접 지역 소진 시 `null`(오늘 소개 없음).
- **성별 전용 폴백 풀 제거**: 근접 지역 전체 순회가 성별 풀 전체를 포섭하므로 불필요. `MatchPoolByGender`, `popByGender/pushBackByGender/removeByGender` **삭제**(단순성 우선).
- 매칭된 두 유저는 각자의 `(gender, regionId)` 풀에서 제거(`remove`). 각 유저의 `regionId`는 풀 적재 시 만든 `Map<userId, regionId>`로 확보.

## 6. 데이터 / 스키마 변경

### 6.1 변경

- `GetMatchCandidatePort.findOneCandidate(...)` 시그니처(§4).
- `MatchPoolPort`: `pop/pushBack/remove`의 `regionCode: Int` → `regionId: Long`. 성별 폴백 메서드 3개(`popByGender/pushBackByGender/removeByGender`) 삭제.
- `SaveMatchPoolPort`: `saveByGender` 삭제(성별 폴백 풀 제거).
- `MatchPoolGroup.regionCode: Int` → `regionId: Long`(그룹 키도 `gender to regionId`). `MatchPoolByGender` 삭제.
- DTO: `ActiveUser.regionCode → regionId`, `MatchBatchTarget.regionCode → regionId`. 해당 `*DaoImpl` 프로젝션을 `regionId`로.
- `MatchUserJpaRepository`의 온보딩 후보 쿼리: `region_code` 등식 → `region_id` 기반 + 이력 제외. (어댑터가 근접 지역 순회 시 사용)
- `MatchRedisAdapter`: 키 포맷 `match:pool:{gender}:{regionId}`. 성별 전용 키 로직 삭제. (옛 키는 TTL 2일로 자연 만료 → 데이터 마이그레이션 불필요)
- `RunDailyMatchBatchService` / `MatchIntroducer`: `regionCode` 인자 → `regionId`, 근접 순회 로직.

### 6.2 유지 (팀 보존 — 손대지 않음)

- `match_user.region_code` 컬럼, 인덱스 `idx_gender_region_code_last_login_at`.
- `UserDetail.regionCode` / `UserDetailEntity.region_code`.
- `Region.resolveAreaCode`(common), `MatchProfileSnapshot.regionCode`.
- `MatchUser.regionCode`(도메인 필드 — 엔티티 `region_code` 기록용으로 잔존).
- 팀 DAO들(`GetCandidateTeamDaoImpl`, `GetRecommendableSoloUserDaoImpl`, `RecommendableSoloUser` DTO 등).

### 6.3 DB 마이그레이션 SQL (`docs/migration/`)

- `match_user`에 인덱스 신규: `idx_gender_region_id_last_login_at (gender, region_id, last_login_at)` — 온보딩 후보 seek용(`gender=` AND `region_id IN(...)` AND `last_login_at>=` 받침).
- 컬럼 드롭 없음(팀이 `region_code` 사용). `ddl-auto: update` 환경이므로 인덱스 추가도 SQL 파일로 명시 적용.

## 7. 테스트 전략

- **`RegionProximity` 순수 계산기 → Kotest 유닛**: 가까운 지역이 먼저, 자기 지역 거리 0, 좌표 동률 시 안정 정렬.
- **`MatchIntroducer` 근접 순회 → 유닛**(가짜 포트): 가까운 지역 우선 채택, history 충돌 시 다음 후보/지역으로 진행, 전 지역 소진 시 `null`.
- **E2E(meeple-api)**: 온보딩 추천·일일 배치 기존 E2E를 `regionId` 기준으로 갱신. `AbstractIntegrationSupport` + 픽스처 + `RestAssuredDsl`. 거리 우선/이력 제외가 결과에 반영되는지 검증.

## 8. 성능 / 트레이드오프

- **배치**: 최악의 경우 한 타깃이 빈 근접 지역 여러 개를 거칠 수 있음. 야간 배치라 정확성 우선으로 전체 근접 지역을 순회(풀이 소진되며 자연 축소). 지역당 재시도는 `MAX_CANDIDATE_ATTEMPTS`로 상한.
- **온보딩**: 보통 가장 가까운 1~3개 지역에서 종료. 인덱스 `idx_gender_region_id_last_login_at`로 지역별 후보 조회 seek.
- 두 경로 모두 **"근접 지역을 순서대로 순회"**라는 동일한 멘탈 모델 → 가독성·일관성.

## 9. 영향 파일 (참고, 계획 단계에서 확정)

- core: `match/.../RecommendMatchService.kt`, `.../port/out/GetMatchCandidatePort.kt`
- scheduler: `match/command/application/MatchIntroducer.kt`, `RunDailyMatchBatchService.kt`, `.../port/out/MatchPoolPort.kt`, `SaveMatchPoolPort.kt`, 신규 `RegionProximityPort.kt`, `domain/MatchPoolGroup.kt`, (삭제) `MatchPoolByGender.kt`, `query/dto/{ActiveUser,MatchBatchTarget}.kt`
- infra: `match/command/adapter/{MatchUserAdapter,MatchRedisAdapter}.kt`, `match/command/repository/MatchUserJpaRepository.kt`, `match/command/entity/MatchUserEntity.kt`(인덱스), `user/query/GetActiveUserDaoImpl.kt`, `match/query/GetMatchBatchTargetDaoImpl.kt`, 신규 `region/.../RegionProximityRegistry.kt` + 신규 `RegionProximityPort` 어댑터
- infra/region: 신규 `RegionProximity`(순수 계산기) + `RegionProximityRegistry`
- docs/migration: 신규 인덱스 SQL
