# 일일 솔로 배치 근접 지역 셔플 (다양성) 설계

작성일: 2026-06-23
브랜치: `main` 기준(작업 브랜치 분기 예정)

## 1. 배경 / 목표

`SoloMatchBatchService.findNearestFreshPartner`는 `regionProximityPort.nearbyRegionIds(target.regionId)`(가까운 순)를 그대로 순회해 **항상 가장 가까운 신선 후보**를 고른다. 그 결과 매번 같은 근접 후보부터 평가돼 **매칭 다양성이 떨어진다**.

가장 가까운 **10개 지역의 순회 순서를 무작위로 섞어** 다양성을 준다. 매칭 상대는 여전히 "가장 가까운 10개 지역 안"(= 충분히 가까움)이지만, 그중 누구냐는 실행마다 달라진다.

**비목표(확정)**
- 효율 개선(빈깡통 조회 = `existsByPair` N+1 제거)은 이번 범위가 **아니다**. `existsByPair` 호출은 그대로 둔다. (다양성과 무관하며 별개 작업으로 남긴다.)
- K(10) 설정화하지 않는다. 상수.

## 2. 동작 변경

`findNearestFreshPartner(target, pool)`:
- 기존: `for (regionId in regionProximityPort.nearbyRegionIds(target.regionId))`
- 변경: `for (regionId in regionShuffler.shuffleNearest(regionProximityPort.nearbyRegionIds(target.regionId)))`
- 지역 내 후보 순서(`pool.freshCandidates` = lastLogin desc)와 `existsByPair`(재소개 이력 스킵)는 **불변**.

`shuffleNearest`의 의미:
- 입력(가까운 순 regionId 목록)의 **앞 K(=10)개만 무작위로 섞고**, K번째 이후는 가까운 순 그대로 이어 붙인다.
- 입력이 K개 미만이면 있는 만큼 전부 섞는다.
- 즉 "가장 가까운 10개 안에서 무작위, 그 밖은 여전히 가까운 순 fallback".

## 3. 무작위성 주입 seam (결정성/테스트)

이 프로젝트는 비결정성을 out-port로 주입한다(`TimeGenerator` 선례, 모듈별 자체 보유). 셔플도 동일하게 격리한다.

- **out-port** `com.org.meeple.scheduler.match.command.application.port.out.RegionShuffler`
  ```kotlin
  interface RegionShuffler {
      /** 가까운 순 regionId 목록에서 앞 K개만 무작위로 섞은 새 목록을 반환한다. (K번째 이후는 순서 유지) */
      fun shuffleNearest(regionIds: List<Long>): List<Long>
  }
  ```
- **운영 구현** `RandomRegionShuffler`(scheduler 소유, 프레임워크 비의존 — `SystemBatchTimeGenerator`와 같은 위치/관례). `kotlin.random.Random`으로 앞 K개를 섞는다. `NEAREST_SHUFFLE_COUNT = 10` 상수를 구현체 companion에 둔다.
  ```kotlin
  @Component
  class RandomRegionShuffler(private val random: Random = Random.Default) : RegionShuffler {
      override fun shuffleNearest(regionIds: List<Long>): List<Long> {
          if (regionIds.size <= 1) return regionIds
          val head: List<Long> = regionIds.take(NEAREST_SHUFFLE_COUNT).shuffled(random)
          val tail: List<Long> = regionIds.drop(NEAREST_SHUFFLE_COUNT)
          return head + tail
      }
      companion object { private const val NEAREST_SHUFFLE_COUNT = 10 }
  }
  ```
- `SoloMatchBatchService`는 생성자에 `RegionShuffler`를 주입받아 `findNearestFreshPartner`에서 사용한다. 무작위성이 한 곳에 격리돼 서비스/통합 테스트를 결정적으로 만들 수 있다.

## 4. 테스트 전략

- **신규 유닛** `RandomRegionShufflerTest`(Kotest): 시드 고정 `Random`으로
  - 앞 10개가 입력과 다른 순서로 섞이고(시드로 결정적 검증), **11번째 이후는 순서 불변**.
  - 입력이 0/1개면 그대로 반환.
  - 입력이 10개 이하면 전체가 셔플 대상(뒤가 빈 리스트).
- **통합 테스트 결정성**: `RunSoloMatchBatchIntegrationTest`의 "가까운 지역과 먼 지역 → 가까운 지역 후보와 소개" 단언은 실 셔플러로는 비결정적이 된다. → 통합 테스트 컨텍스트에 **항등(identity) `RegionShuffler`**(입력 그대로 반환) 빈을 주입해 가까운 순서를 보존하고 단언을 유지한다. (셔플 자체는 유닛에서 검증)
  - 주입 방법: `AbstractIntegrationSupport`가 쓰는 테스트 설정에 `@TestConfiguration`/`@Primary` 빈으로 항등 `RegionShuffler` 등록(테스트 소스). 운영 `RandomRegionShuffler`를 덮어쓴다.

## 5. 영향 파일

- 신규: `meeple-scheduler/.../port/out/RegionShuffler.kt`, `meeple-scheduler/.../<adapter|application>/RandomRegionShuffler.kt`(SystemBatchTimeGenerator와 같은 패키지 관례), 유닛 테스트 `RandomRegionShufflerTest`.
- 수정: `SoloMatchBatchService`(생성자 주입 + `findNearestFreshPartner` 한 줄), `RunSoloMatchBatchIntegrationTest`(항등 셔플러 빈 + 기존 단언 유지), 필요 시 테스트 설정.
- `// TODO 개선` 주석 제거(이 작업이 그 TODO의 해소).

## 6. 트레이드오프 (명시)

- 가장 가까운 사람이 항상 뽑히지 않는다 — 10개 지역 안에서 무작위라 10번째로 먼 지역의 후보가 1번째보다 먼저 뽑힐 수 있다. "가까운 10개 안"이라는 근접성 경계는 유지된다. (사용자가 다양성을 위해 수용)
- 효율(빈깡통 `existsByPair` N+1)은 개선되지 않는다(범위 밖).
