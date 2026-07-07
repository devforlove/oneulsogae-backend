# 유저용 타입별 그룹핑 모임 리스트 조회 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로그인 유저가 모집중(RECRUITING) 모임을 모임 타입별로 그룹핑된 리스트로 조회하는 `GET /gatherings/v1` API를 추가한다.

**Architecture:** 헥사고날 + CQRS의 조회 전용(query-only) 경로. 유저는 모임을 생성하지 않으므로 command 측 없이 `meeple-core`에 유저용 gathering query 도메인을 신설한다(기존 `meeple-admin`의 `AdminGathering`과 별개, 같은 `gatherings` 테이블을 읽는 독립 read model). 컨트롤러는 `meeple-api`, QueryDSL DAO 구현은 `meeple-infra`에 두고 이미지 presign은 기존 S3 어댑터를 재사용한다.

**Tech Stack:** Kotlin 2.2.21 / JVM 21, Spring Boot 4.0.6, Spring Data JPA + QueryDSL, Kotest(DescribeSpec) + RestAssured E2E(Testcontainers).

## Global Constraints

- 응답·주석·문서는 한국어로 작성한다.
- `meeple-backend`만 수정한다. 프론트엔드는 건드리지 않는다(아래 "프론트엔드 안내" 참고).
- 타입 명시: 변수·반환 타입·람다 파라미터 타입을 생략하지 않는다(표현식 본문 함수 포함).
- 조회 서비스는 `@Transactional(readOnly = true)`, 부수효과(저장/상태변경) 금지.
- 조회는 도메인 모델이 아니라 전용 read model(DTO/프로젝션)을 반환한다. query는 자기 dao에만 의존한다.
- 컬렉션은 원시 `List` 대신 `Xs`(복수형) 일급 컬렉션으로 감싼다.
- 도메인 규칙(그룹핑)은 서비스에 인라인하지 않고 일급 컬렉션 메서드로 캡슐화한다.
- 들여쓰기는 탭(기존 파일 스타일). 새 파일도 탭으로 작성한다.
- 패키지 베이스: core = `com.org.meeple.core.<domain>`, infra = `com.org.meeple.infra.<domain>`, api = `com.org.meeple.api.<domain>`, 공용 enum = `com.org.meeple.common.<domain>`.

## 확정 요구사항

- **엔드포인트**: `GET /gatherings/v1` (로그인 유저 전용 — SecurityConfig의 `anyRequest().authenticated()`가 강제하므로 SecurityConfig 수정 불필요).
- **상태 필터**: `GatheringStatus.RECRUITING`만.
- **그룹핑**: `GatheringType` 3개(ONE_ON_ONE_ROTATION → COOKING → PARTY)를 enum 선언 순서로 항상 모두 포함, 없는 타입은 빈 배열.
- **그룹 내 정렬**: `gatheringAt` 오름차순(임박순), 개수 제한 없음.
- **단건 필드**: `id`, `imageUrl`(presigned, 이미지 없으면 null), `region`(장소), `title`(제목), `gatheringAt`(시간).

## File Structure

**신규 (meeple-core — `com.org.meeple.core.gathering.query`)**
- `dto/GatheringView.kt` — 단건 read model(내부용 imageKey + imageUrl). 책임: 조회 한 행 표현.
- `dto/GatheringViews.kt` — 일급 컬렉션 + `groupByType(types)`. 책임: 목록 보관 + 타입별 그룹핑 캡슐화.
- `dto/GatheringTypeGroup.kt` — 타입 1개 그룹(type, typeDescription, gatherings). 책임: 그룹 한 덩어리 표현.
- `dto/GroupedGatherings.kt` — 그룹 일급 컬렉션. 책임: 그룹 목록 보관.
- `dao/GetGatheringDao.kt` — query out-port. 책임: 모집중 목록 조회 계약.
- `service/port/in/GetGatheringsUseCase.kt` — in-port. 책임: 유스케이스 계약.
- `service/port/out/GatheringImageUrlPort.kt` — 이미지 presign out-port(core 소속). 책임: 이미지 URL 발급 계약.
- `service/GetGatheringsService.kt` — 유스케이스 구현. 책임: 조회 → presign 치환 → 그룹핑.

**신규 (meeple-infra — `com.org.meeple.infra.gathering.query`)**
- `GetGatheringDaoImpl.kt` — `GetGatheringDao`의 QueryDSL 구현.

**수정 (meeple-infra)**
- `gathering/query/S3GatheringImageUrlAdapter.kt` — core `GatheringImageUrlPort`도 함께 구현(dual interface).

**신규 (meeple-api — `com.org.meeple.api.gathering`)**
- `GatheringController.kt` — `GET /gatherings/v1`.
- `response/GatheringGroupListResponse.kt` — 응답 DTO(groups/Group/Item + `from`).

**수정 (meeple-api 테스트)**
- `common/config/TestFileStorageConfig.kt` — core `GatheringImageUrlPort` 페이크 빈 추가.

**신규 (테스트)**
- `meeple-api/src/test/.../domain/gathering/GatheringViewsTest.kt` — `groupByType` 유닛 테스트(Kotest).
- `meeple-api/src/test/.../api/gathering/GatheringQueryE2ETest.kt` — 엔드포인트 E2E.

---

### Task 1: core 조회 read model + 타입별 그룹핑

**Files:**
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/dto/GatheringView.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/dto/GatheringTypeGroup.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/dto/GroupedGatherings.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/dto/GatheringViews.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/domain/gathering/GatheringViewsTest.kt`

**Interfaces:**
- Produces:
  - `GatheringView(id: Long, type: GatheringType, title: String, imageKey: String?, imageUrl: String? = null, region: String, gatheringAt: LocalDateTime)` + 보조 생성자 `(id, type, title, imageKey, region, gatheringAt)`(imageUrl=null).
  - `GatheringViews(values: List<GatheringView>)`, `fun groupByType(types: List<GatheringType>): GroupedGatherings`, `companion object { fun empty(): GatheringViews }`.
  - `GatheringTypeGroup(type: GatheringType, typeDescription: String, gatherings: List<GatheringView>)`.
  - `GroupedGatherings(values: List<GatheringTypeGroup>)`.

- [ ] **Step 1: DTO 데이터 클래스 4개를 생성한다**

`GatheringView.kt`:
```kotlin
package com.org.meeple.core.gathering.query.dto

import com.org.meeple.common.gathering.GatheringType
import java.time.LocalDateTime

/**
 * 유저용 모임 목록 한 건(read model).
 * dao는 [imageKey]까지 채우고 [imageUrl]은 null로 둔다. 서비스가 presign 결과로 [imageUrl]을 채운다(이미지 없으면 null).
 */
data class GatheringView(
	val id: Long,
	val type: GatheringType,
	val title: String,
	val imageKey: String?,
	val imageUrl: String? = null,
	val region: String,
	val gatheringAt: LocalDateTime,
) {
	/** dao 투영용 생성자. imageUrl은 서비스가 presign으로 채운다. */
	constructor(
		id: Long,
		type: GatheringType,
		title: String,
		imageKey: String?,
		region: String,
		gatheringAt: LocalDateTime,
	) : this(id, type, title, imageKey, null, region, gatheringAt)
}
```

`GatheringTypeGroup.kt`:
```kotlin
package com.org.meeple.core.gathering.query.dto

import com.org.meeple.common.gathering.GatheringType

/** 모임 타입 한 종류의 그룹. [gatherings]는 해당 타입 모임 목록(없으면 빈 리스트). */
data class GatheringTypeGroup(
	val type: GatheringType,
	val typeDescription: String,
	val gatherings: List<GatheringView>,
)
```

`GroupedGatherings.kt`:
```kotlin
package com.org.meeple.core.gathering.query.dto

/** 타입별 그룹([GatheringTypeGroup]) 목록의 일급 컬렉션. */
data class GroupedGatherings(
	val values: List<GatheringTypeGroup>,
)
```

- [ ] **Step 2: `GatheringViews`(일급 컬렉션 + groupByType)를 생성한다**

`GatheringViews.kt`:
```kotlin
package com.org.meeple.core.gathering.query.dto

import com.org.meeple.common.gathering.GatheringType

/**
 * 유저용 모임 목록([GatheringView])의 일급 컬렉션.
 * 원시 List를 그대로 노출하지 않고 감싸, 타입별 그룹핑 등 컬렉션 동작을 한곳에 응집시킨다.
 */
data class GatheringViews(
	val values: List<GatheringView>,
) {

	/**
	 * [types] 선언 순서대로 타입별 그룹을 만든다. 각 그룹은 이 목록에서 해당 타입 행만 추린 것으로,
	 * 원래 순서(gatheringAt 오름차순)를 유지한다. 해당 타입 모임이 없으면 빈 리스트 그룹으로 포함한다.
	 */
	fun groupByType(types: List<GatheringType>): GroupedGatherings =
		GroupedGatherings(
			types.map { type: GatheringType ->
				GatheringTypeGroup(
					type = type,
					typeDescription = type.description,
					gatherings = values.filter { view: GatheringView -> view.type == type },
				)
			},
		)

	companion object {

		/** 빈 목록. */
		fun empty(): GatheringViews = GatheringViews(emptyList())
	}
}
```

- [ ] **Step 3: 실패하는 유닛 테스트를 작성한다**

`GatheringViewsTest.kt`:
```kotlin
package com.org.meeple.domain.gathering

import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.core.gathering.query.dto.GatheringView
import com.org.meeple.core.gathering.query.dto.GatheringViews
import com.org.meeple.core.gathering.query.dto.GroupedGatherings
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class GatheringViewsTest : DescribeSpec({

	val baseTime: LocalDateTime = LocalDateTime.of(2026, 8, 1, 19, 0, 0)

	fun view(id: Long, type: GatheringType): GatheringView =
		GatheringView(
			id = id,
			type = type,
			title = "모임-$id",
			imageKey = null,
			region = "서울 강남구",
			gatheringAt = baseTime,
		)

	describe("GatheringViews.groupByType") {

		it("전달한 타입 선언 순서대로 그룹을 만든다") {
			val views: GatheringViews = GatheringViews(
				listOf(view(1L, GatheringType.PARTY), view(2L, GatheringType.COOKING)),
			)

			val grouped: GroupedGatherings = views.groupByType(GatheringType.entries)

			grouped.values.map { it.type } shouldBe listOf(
				GatheringType.ONE_ON_ONE_ROTATION,
				GatheringType.COOKING,
				GatheringType.PARTY,
			)
			grouped.values[0].typeDescription shouldBe "1:1 로테이션"
		}

		it("해당 타입 모임이 없으면 빈 배열 그룹으로 포함한다") {
			val views: GatheringViews = GatheringViews(listOf(view(1L, GatheringType.COOKING)))

			val grouped: GroupedGatherings = views.groupByType(GatheringType.entries)

			grouped.values.first { it.type == GatheringType.ONE_ON_ONE_ROTATION }.gatherings shouldBe emptyList()
			grouped.values.first { it.type == GatheringType.PARTY }.gatherings shouldBe emptyList()
			grouped.values.first { it.type == GatheringType.COOKING }.gatherings.map { it.id } shouldBe listOf(1L)
		}

		it("그룹 내에서 원래(입력) 순서를 유지한다") {
			val views: GatheringViews = GatheringViews(
				listOf(view(10L, GatheringType.PARTY), view(20L, GatheringType.PARTY)),
			)

			val grouped: GroupedGatherings = views.groupByType(GatheringType.entries)

			grouped.values.first { it.type == GatheringType.PARTY }.gatherings.map { it.id } shouldBe listOf(10L, 20L)
		}
	}
})
```

- [ ] **Step 4: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.domain.gathering.GatheringViewsTest"`
Expected: PASS (3 tests). (DTO를 먼저 만들었으므로 컴파일·통과한다. 만약 Step 1~2를 건너뛰면 컴파일 실패로 FAIL — 그 경우 DTO부터 만든다.)

- [ ] **Step 5: 커밋**

```bash
git add meeple-core/src/main/kotlin/com/org/meeple/core/gathering \
        meeple-api/src/test/kotlin/com/org/meeple/domain/gathering/GatheringViewsTest.kt
git commit -m "feat(gathering): 유저 모임 목록 read model과 타입별 그룹핑 추가"
```

---

### Task 2: core query 포트 + 조회 서비스

**Files:**
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/dao/GetGatheringDao.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/service/port/in/GetGatheringsUseCase.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/service/port/out/GatheringImageUrlPort.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/service/GetGatheringsService.kt`

**Interfaces:**
- Consumes (Task 1): `GatheringViews`, `GatheringView`, `GroupedGatherings`.
- Produces:
  - `interface GetGatheringDao { fun findRecruitingOrderByGatheringAt(): GatheringViews }`
  - `interface GetGatheringsUseCase { fun getGatherings(): GroupedGatherings }`
  - `fun interface GatheringImageUrlPort { fun presignedGetUrl(imageKey: String): String }`
  - `class GetGatheringsService(...) : GetGatheringsUseCase`

- [ ] **Step 1: query out-port(DAO)를 생성한다**

`GetGatheringDao.kt`:
```kotlin
package com.org.meeple.core.gathering.query.dao

import com.org.meeple.core.gathering.query.dto.GatheringViews

/**
 * 유저용 모임 조회 dao(query out-port). (조회 전용 read model 반환)
 * 모집중(RECRUITING) 모임만 모임 일시 임박순으로 조회한다. 실제 구현은 infra 레이어가 담당한다.
 */
interface GetGatheringDao {

	/** 모집중 모임을 gatheringAt 오름차순(임박순)으로 조회한다. (imageUrl은 서비스가 채운다) */
	fun findRecruitingOrderByGatheringAt(): GatheringViews
}
```

- [ ] **Step 2: in-port(UseCase)와 out-port(이미지 URL)를 생성한다**

`GetGatheringsUseCase.kt`:
```kotlin
package com.org.meeple.core.gathering.query.service.port.`in`

import com.org.meeple.core.gathering.query.dto.GroupedGatherings

/** 유저용 모임 목록 조회 인포트(유스케이스). */
interface GetGatheringsUseCase {

	/** 모집중 모임을 타입별로 그룹핑해 조회한다. (모든 타입 포함, 타입 내 gatheringAt 임박순) */
	fun getGatherings(): GroupedGatherings
}
```

`GatheringImageUrlPort.kt`:
```kotlin
package com.org.meeple.core.gathering.query.service.port.out

/**
 * 모임 대표 이미지의 열람용 URL 발급 out-port. (S3 presigned GET URL)
 * core는 인터페이스만 소유하고, infra가 S3Presigner로 구현한다.
 */
fun interface GatheringImageUrlPort {

	/** [imageKey](S3 오브젝트 키)에 대한, 일정 시간 유효한 열람용 URL을 발급한다. */
	fun presignedGetUrl(imageKey: String): String
}
```

- [ ] **Step 3: 조회 서비스를 생성한다**

`GetGatheringsService.kt`:
```kotlin
package com.org.meeple.core.gathering.query.service

import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.core.gathering.query.dao.GetGatheringDao
import com.org.meeple.core.gathering.query.dto.GatheringView
import com.org.meeple.core.gathering.query.dto.GatheringViews
import com.org.meeple.core.gathering.query.dto.GroupedGatherings
import com.org.meeple.core.gathering.query.service.port.`in`.GetGatheringsUseCase
import com.org.meeple.core.gathering.query.service.port.out.GatheringImageUrlPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetGatheringsUseCase] 구현. (조회 전용 - 쓰기 부수효과 없음)
 * 모집중 모임을 gatheringAt 임박순으로 조회하고, 각 행의 대표 이미지 키(imageKey)를
 * presigned 열람 URL(imageUrl)로 변환한 뒤(이미지 없으면 null), 모임 타입별로 그룹핑한다.
 * (타입 3종을 항상 모두 포함하고, 해당 타입 모임이 없으면 빈 배열)
 */
@Service
@Transactional(readOnly = true)
class GetGatheringsService(
	private val getGatheringDao: GetGatheringDao,
	private val gatheringImageUrlPort: GatheringImageUrlPort,
) : GetGatheringsUseCase {

	override fun getGatherings(): GroupedGatherings {
		val rows: GatheringViews = getGatheringDao.findRecruitingOrderByGatheringAt()
		val withUrls: GatheringViews = GatheringViews(
			rows.values.map { view: GatheringView ->
				view.copy(imageUrl = presignedUrlOf(view.imageKey))
			},
		)
		return withUrls.groupByType(GatheringType.entries)
	}

	// 대표 이미지가 없으면 null, 있으면 열람용 presigned URL.
	private fun presignedUrlOf(imageKey: String?): String? =
		imageKey?.let { key: String -> gatheringImageUrlPort.presignedGetUrl(key) }
}
```

- [ ] **Step 4: core 모듈 컴파일을 확인한다**

Run: `./gradlew :meeple-core:compileKotlin`
Expected: BUILD SUCCESSFUL. (DAO 구현이 아직 없어도 core는 인터페이스만 참조하므로 컴파일된다.)

- [ ] **Step 5: 커밋**

```bash
git add meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query
git commit -m "feat(gathering): 유저 모임 조회 유스케이스와 포트 추가"
```

---

### Task 3: infra QueryDSL DAO 구현 + S3 presign 어댑터 재사용

**Files:**
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/gathering/query/GetGatheringDaoImpl.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/gathering/query/S3GatheringImageUrlAdapter.kt`

**Interfaces:**
- Consumes (Task 1·2): `GatheringView`, `GatheringViews`, `GetGatheringDao`, core `GatheringImageUrlPort`.
- Consumes (기존): `QGatheringEntity.gatheringEntity`(생성된 Q타입), `JPAQueryFactory`, `com.org.meeple.common.gathering.GatheringStatus`.
- Produces: `GetGatheringDaoImpl`(@Component), 확장된 `S3GatheringImageUrlAdapter`.

- [ ] **Step 1: QueryDSL DAO 구현을 생성한다**

`GetGatheringDaoImpl.kt`:
```kotlin
package com.org.meeple.infra.gathering.query

import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.core.gathering.query.dao.GetGatheringDao
import com.org.meeple.core.gathering.query.dto.GatheringView
import com.org.meeple.core.gathering.query.dto.GatheringViews
import com.org.meeple.infra.gathering.command.entity.QGatheringEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetGatheringDao]의 QueryDSL 구현. (조회 전용)
 * 모집중(RECRUITING) 모임을 모임 일시(gathering_at) 오름차순(임박순)으로 read model에 직접 투영한다.
 * imageKey까지만 담고 imageUrl은 서비스가 presign으로 채운다. (soft delete 행은 @SQLRestriction으로 제외)
 * 복합 인덱스 idx_status_type_gathering_at의 선두 status 동등 조건을 활용한다.
 */
@Component
class GetGatheringDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetGatheringDao {

	override fun findRecruitingOrderByGatheringAt(): GatheringViews {
		val gathering: QGatheringEntity = QGatheringEntity.gatheringEntity
		val views: List<GatheringView> = queryFactory
			.select(
				Projections.constructor(
					GatheringView::class.java,
					gathering.id,
					gathering.type,
					gathering.title,
					gathering.imageKey,
					gathering.region,
					gathering.gatheringAt,
				),
			)
			.from(gathering)
			.where(gathering.status.eq(GatheringStatus.RECRUITING))
			.orderBy(gathering.gatheringAt.asc())
			.fetch()
		return GatheringViews(views)
	}
}
```

> 주의: `Projections.constructor`의 6개 인자는 `GatheringView`의 **보조 생성자**(id, type, title, imageKey, region, gatheringAt) 시그니처와 정확히 일치해야 한다. 순서·타입이 어긋나면 런타임에 생성자 매칭 실패한다.

- [ ] **Step 2: 기존 S3 어댑터가 core 포트도 구현하도록 수정한다**

`S3GatheringImageUrlAdapter.kt`의 import와 클래스 선언을 아래처럼 바꾼다(두 포트가 동일 시그니처 `presignedGetUrl(String): String`라 오버라이드 하나로 양쪽을 충족한다). 메서드 본문(`presignedGetUrl`)은 그대로 둔다.

기존:
```kotlin
import com.org.meeple.admin.gathering.query.service.port.out.GatheringImageUrlPort
```
```kotlin
class S3GatheringImageUrlAdapter(
	private val s3Presigner: S3Presigner,
	private val s3Properties: S3Properties,
) : GatheringImageUrlPort {
```

수정 후:
```kotlin
import com.org.meeple.admin.gathering.query.service.port.out.GatheringImageUrlPort as AdminGatheringImageUrlPort
import com.org.meeple.core.gathering.query.service.port.out.GatheringImageUrlPort as UserGatheringImageUrlPort
```
```kotlin
class S3GatheringImageUrlAdapter(
	private val s3Presigner: S3Presigner,
	private val s3Properties: S3Properties,
) : AdminGatheringImageUrlPort, UserGatheringImageUrlPort {
```

그리고 클래스 KDoc의 `[GatheringImageUrlPort]` 참조가 컴파일 경고 없이 유지되도록, 첫 줄을 다음으로 바꾼다:
```kotlin
/**
 * 모임 대표 이미지의 presigned GET URL 발급 어댑터. (어드민·유저 두 [GatheringImageUrlPort]를 함께 구현)
```
(원문 `[GatheringImageUrlPort]의 S3 구현.` 한 줄만 위 문장으로 교체. 나머지 KDoc은 유지.)

- [ ] **Step 3: infra 모듈 컴파일을 확인한다**

Run: `./gradlew :meeple-infra:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 커밋**

```bash
git add meeple-infra/src/main/kotlin/com/org/meeple/infra/gathering/query/GetGatheringDaoImpl.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/gathering/query/S3GatheringImageUrlAdapter.kt
git commit -m "feat(gathering): 유저 모임 조회 QueryDSL DAO 추가 및 presign 어댑터 재사용"
```

---

### Task 4: api 컨트롤러 + 응답 DTO

**Files:**
- Create: `meeple-api/src/main/kotlin/com/org/meeple/api/gathering/response/GatheringGroupListResponse.kt`
- Create: `meeple-api/src/main/kotlin/com/org/meeple/api/gathering/GatheringController.kt`

**Interfaces:**
- Consumes (Task 1·2): `GetGatheringsUseCase`, `GroupedGatherings`, `GatheringTypeGroup`, `GatheringView`, `ApiResponse`.
- Produces: `GatheringGroupListResponse(groups: List<Group>)` + `fun from(GroupedGatherings)`, `GatheringController`(`GET /gatherings/v1`).

- [ ] **Step 1: 응답 DTO를 생성한다**

`GatheringGroupListResponse.kt`:
```kotlin
package com.org.meeple.api.gathering.response

import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.core.gathering.query.dto.GatheringTypeGroup
import com.org.meeple.core.gathering.query.dto.GatheringView
import com.org.meeple.core.gathering.query.dto.GroupedGatherings
import java.time.LocalDateTime

/**
 * 유저용 모임 목록 응답. 모임 타입별 그룹([groups])으로 내려준다.
 * 타입 3종을 항상 모두 포함하며, 해당 타입 모임이 없으면 [Group.gatherings]가 빈 배열이다.
 */
data class GatheringGroupListResponse(
	val groups: List<Group>,
) {

	/** 모임 타입 한 종류의 그룹. */
	data class Group(
		val type: GatheringType,
		val typeDescription: String,
		val gatherings: List<Item>,
	)

	/** 모임 한 건(목록 항목). */
	data class Item(
		val id: Long,
		val imageUrl: String?,
		val region: String,
		val title: String,
		val gatheringAt: LocalDateTime,
	)

	companion object {

		fun from(grouped: GroupedGatherings): GatheringGroupListResponse =
			GatheringGroupListResponse(
				groups = grouped.values.map { group: GatheringTypeGroup ->
					Group(
						type = group.type,
						typeDescription = group.typeDescription,
						gatherings = group.gatherings.map { view: GatheringView ->
							Item(
								id = view.id,
								imageUrl = view.imageUrl,
								region = view.region,
								title = view.title,
								gatheringAt = view.gatheringAt,
							)
						},
					)
				},
			)
	}
}
```

- [ ] **Step 2: 컨트롤러를 생성한다**

`GatheringController.kt`:
```kotlin
package com.org.meeple.api.gathering

import com.org.meeple.api.gathering.response.GatheringGroupListResponse
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.gathering.query.service.port.`in`.GetGatheringsUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 유저 모임 엔드포인트. (인증 필요 - SecurityConfig anyRequest().authenticated())
 * - GET /: 모집중(RECRUITING) 모임을 모임 타입별로 그룹핑해 조회한다. (타입 3종 모두 포함, 타입 내 gatheringAt 임박순)
 */
@Tag(name = "모임", description = "유저용 모임 조회 엔드포인트. 모집중 모임을 타입별로 그룹핑해 내려준다.")
@RestController
@RequestMapping("/gatherings/v1")
class GatheringController(
	private val getGatheringsUseCase: GetGatheringsUseCase,
) {

	@Operation(
		summary = "모집중 모임 목록(타입별 그룹) 조회",
		description = "모집중(RECRUITING) 모임을 모임 타입별 그룹으로 조회한다. 타입 3종(1:1 로테이션·쿠킹·파티)을 항상 모두 포함하고, " +
			"해당 타입 모임이 없으면 빈 배열이다. 각 그룹 내부는 모임 일시(gatheringAt) 임박순으로 정렬한다. " +
			"항목은 imageUrl(presigned)·region(장소)·title(제목)·gatheringAt(시간)을 포함한다.",
	)
	@GetMapping
	fun gatherings(): ApiResponse<GatheringGroupListResponse> =
		ApiResponse.success(GatheringGroupListResponse.from(getGatheringsUseCase.getGatherings()))
}
```

- [ ] **Step 3: api 모듈 컴파일을 확인한다**

Run: `./gradlew :meeple-api:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 커밋**

```bash
git add meeple-api/src/main/kotlin/com/org/meeple/api/gathering
git commit -m "feat(gathering): 유저 모임 목록(타입별 그룹) 조회 엔드포인트 추가"
```

---

### Task 5: E2E 테스트 + 테스트용 presign 페이크 배선

**Files:**
- Modify: `meeple-api/src/test/kotlin/com/org/meeple/common/config/TestFileStorageConfig.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/api/gathering/GatheringQueryE2ETest.kt`

**Interfaces:**
- Consumes: core `GatheringImageUrlPort`, `GatheringEntityFixture`, `IntegrationUtil`, `AbstractIntegrationSupport`, `QGatheringEntity`, RestAssured DSL(`get`/`expect`/`bearer`), `accessTokenFor`.

- [ ] **Step 1: 테스트 설정에 core `GatheringImageUrlPort` 페이크 빈을 추가한다**

`TestFileStorageConfig.kt`의 admin `GatheringImageUrlPort` import를 alias로 바꾸고, core용 alias import와 페이크 빈을 추가한다.

기존 import:
```kotlin
import com.org.meeple.admin.gathering.query.service.port.out.GatheringImageUrlPort
```
수정 후 import(두 줄):
```kotlin
import com.org.meeple.admin.gathering.query.service.port.out.GatheringImageUrlPort as AdminGatheringImageUrlPort
import com.org.meeple.core.gathering.query.service.port.out.GatheringImageUrlPort as UserGatheringImageUrlPort
```

기존 빈(admin) 선언을 alias 타입으로 바꾸고, 바로 아래 유저용 빈을 추가한다:
```kotlin
	@Bean
	@Primary
	fun fakeGatheringImageUrlPort(): AdminGatheringImageUrlPort =
		AdminGatheringImageUrlPort { imageKey: String -> "https://presigned.test/$imageKey" }

	@Bean
	@Primary
	fun fakeUserGatheringImageUrlPort(): UserGatheringImageUrlPort =
		UserGatheringImageUrlPort { imageKey: String -> "https://presigned.test/$imageKey" }
```

(다른 빈 `fakeFileStoragePort`/`fakeCompanyVerificationImageUrlPort`/`fakeUploadGatheringImagePort`는 그대로 둔다.)

- [ ] **Step 2: 실패하는 E2E 테스트를 작성한다**

`GatheringQueryE2ETest.kt`:
```kotlin
package com.org.meeple.api.gathering

import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.infra.fixture.GatheringEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.gathering.command.entity.QGatheringEntity
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize
import java.time.LocalDateTime

/**
 * 유저 모임 조회 API E2E 테스트.
 * - GET /gatherings/v1: 모집중(RECRUITING)만, 타입별 그룹(3종 모두 포함, 없으면 빈 배열), 타입 내 gatheringAt 임박순.
 *   항목은 id·imageUrl(presigned)·region·title·gatheringAt을 포함한다.
 * (presigned URL은 TestFileStorageConfig의 페이크로 대체 — https://presigned.test/<imageKey>)
 */
class GatheringQueryE2ETest : AbstractIntegrationSupport({

	describe("GET /gatherings/v1") {

		it("모집중 모임을 타입별 그룹으로 반환하고 타입 3종을 선언 순서로 모두 포함한다") {
			IntegrationUtil.persist(
				GatheringEntityFixture.create(
					title = "쿠킹 모임",
					type = GatheringType.COOKING,
					status = GatheringStatus.RECRUITING,
					imageKey = "gatherings/cooking.png",
					region = "서울 마포구",
					gatheringAt = LocalDateTime.of(2999, 1, 1, 19, 0, 0),
				),
			)

			get("/gatherings/v1") {
				bearer(accessTokenFor(7001L))
			} expect {
				status(200)
				body("success", true)
				// 타입 3종을 선언 순서로 모두 포함한다.
				body("data.groups", hasSize<Any>(3))
				body(
					"data.groups.type",
					contains("ONE_ON_ONE_ROTATION", "COOKING", "PARTY"),
				)
				// 모임 없는 타입은 빈 배열.
				body("data.groups[0].gatherings", hasSize<Any>(0))
				body("data.groups[2].gatherings", hasSize<Any>(0))
				// 쿠킹 그룹에 1건, 필드 확인.
				body("data.groups[1].typeDescription", "쿠킹")
				body("data.groups[1].gatherings", hasSize<Any>(1))
				body("data.groups[1].gatherings[0].title", "쿠킹 모임")
				body("data.groups[1].gatherings[0].region", "서울 마포구")
				body("data.groups[1].gatherings[0].imageUrl", "https://presigned.test/gatherings/cooking.png")
				body("data.groups[1].gatherings[0].gatheringAt", "2999-01-01T19:00:00")
			}
		}

		it("모집중이 아닌 모임(DRAFT·CANCELED·FINISHED 등)은 제외한다") {
			IntegrationUtil.persist(
				GatheringEntityFixture.create(title = "준비중", type = GatheringType.PARTY, status = GatheringStatus.DRAFT),
			)
			IntegrationUtil.persist(
				GatheringEntityFixture.create(title = "취소됨", type = GatheringType.PARTY, status = GatheringStatus.CANCELED),
			)
			IntegrationUtil.persist(
				GatheringEntityFixture.create(title = "모집중", type = GatheringType.PARTY, status = GatheringStatus.RECRUITING),
			)

			get("/gatherings/v1") {
				bearer(accessTokenFor(7001L))
			} expect {
				status(200)
				body("data.groups[2].type", "PARTY")
				body("data.groups[2].gatherings", hasSize<Any>(1))
				body("data.groups[2].gatherings[0].title", "모집중")
			}
		}

		it("같은 타입 그룹 안에서 모임 일시 임박순으로 정렬한다") {
			IntegrationUtil.persist(
				GatheringEntityFixture.create(
					title = "나중",
					type = GatheringType.PARTY,
					status = GatheringStatus.RECRUITING,
					gatheringAt = LocalDateTime.of(2999, 12, 31, 18, 0, 0),
				),
			)
			IntegrationUtil.persist(
				GatheringEntityFixture.create(
					title = "먼저",
					type = GatheringType.PARTY,
					status = GatheringStatus.RECRUITING,
					gatheringAt = LocalDateTime.of(2999, 1, 1, 18, 0, 0),
				),
			)

			get("/gatherings/v1") {
				bearer(accessTokenFor(7001L))
			} expect {
				status(200)
				body("data.groups[2].gatherings[0].title", "먼저")
				body("data.groups[2].gatherings[1].title", "나중")
			}
		}

		it("대표 이미지가 없는 모임은 imageUrl이 null이다") {
			IntegrationUtil.persist(
				GatheringEntityFixture.create(
					title = "이미지 없음",
					type = GatheringType.PARTY,
					status = GatheringStatus.RECRUITING,
					imageKey = null,
				),
			)

			get("/gatherings/v1") {
				bearer(accessTokenFor(7001L))
			} expect {
				status(200)
				body("data.groups[2].gatherings[0].imageUrl", null)
			}
		}

		it("토큰이 없으면 401이다") {
			get("/gatherings/v1") { } expect {
				status(401)
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QGatheringEntity.gatheringEntity)
	}
})
```

- [ ] **Step 3: E2E 테스트를 실행해 통과를 확인한다**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.gathering.GatheringQueryE2ETest"`
Expected: PASS (5 tests). (컨테이너 기동으로 첫 실행은 다소 느리다.)

> 만약 `body("data.groups.type", contains(...))` 경로가 RestAssured JSON 경로에서 리스트 매칭에 실패하면, 개별 인덱스(`data.groups[0].type` 등)로 대체한다. `contains`는 `org.hamcrest.Matchers.contains`이며 순서까지 일치해야 통과한다.

- [ ] **Step 4: 커밋**

```bash
git add meeple-api/src/test/kotlin/com/org/meeple/common/config/TestFileStorageConfig.kt \
        meeple-api/src/test/kotlin/com/org/meeple/api/gathering/GatheringQueryE2ETest.kt
git commit -m "test(gathering): 유저 모임 목록 조회 E2E 및 presign 페이크 배선 추가"
```

---

### Task 6: 전체 검증

- [ ] **Step 1: 신규/수정 테스트를 함께 실행한다**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.domain.gathering.GatheringViewsTest" --tests "com.org.meeple.api.gathering.GatheringQueryE2ETest"`
Expected: PASS (8 tests).

- [ ] **Step 2: 기존 어드민 gathering 테스트가 깨지지 않았는지 확인한다** (S3 어댑터·TestFileStorageConfig를 건드렸으므로 회귀 확인)

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.admin.AdminGathering*"`
Expected: PASS (기존 어드민 조회/생성/수정/상태 E2E 모두 유지).

## 프론트엔드 안내 (별도 조치 — 이 리포에서 수정하지 않음)

신규 응답 계약이므로 프론트엔드에 다음을 반영해야 한다(백엔드에서 직접 고치지 않음):
- 신규 엔드포인트: `GET /gatherings/v1` (인증 토큰 필요).
- 응답: `data.groups: [{ type, typeDescription, gatherings: [{ id, imageUrl, region, title, gatheringAt }] }]`.
- `type`은 `ONE_ON_ONE_ROTATION | COOKING | PARTY`, 3종이 항상 선언 순서로 내려오며 비어 있으면 `gatherings`가 빈 배열. 그룹 내부는 `gatheringAt` 임박순.

## Self-Review

- **Spec coverage**: 소비대상(유저)·경로(`/gatherings/v1`)·RECRUITING 필터(Task 3 where)·타입 그룹핑 3종 항상 포함(Task 1 groupByType)·임박순(Task 3 orderBy)·필드 4종+id(Task 4 Item)·presign 재사용(Task 3)·인덱스 검토(Task 3 주석)·유닛+E2E(Task 1,5) — 모두 태스크로 커버. 경로는 spec의 `/v1/gatherings`에서 코드베이스 관례(`/<도메인>/v1`)에 맞춰 `/gatherings/v1`로 조정(변경 근거 명시).
- **Placeholder scan**: TBD/TODO 없음. 모든 코드 스텝에 실제 코드 포함.
- **Type consistency**: `GatheringView` 보조 생성자(6인자)와 `Projections.constructor`(6인자) 일치. `groupByType(List<GatheringType>)`·`GatheringType.entries` 일관. 서비스/DAO/컨트롤러가 참조하는 타입명(`GatheringViews`,`GroupedGatherings`,`GatheringTypeGroup`,`GetGatheringDao`,`GetGatheringsUseCase`,`GatheringImageUrlPort`) 전부 Task 1·2에서 정의.
