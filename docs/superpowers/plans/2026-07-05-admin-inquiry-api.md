# 어드민 문의(Inquiry) API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 어드민이 사용자 문의를 목록·상세 조회하고 답변할 수 있는 API 3종(`/admin/v1/inquiries`)을 추가한다.

**Architecture:** `meeple-admin`(core 비의존 자립 모듈)에 헥사고날 슬라이스를 추가한다. 조회는 notice/report의 query 패턴(QueryDSL DaoImpl → read model), 답변은 companyverification의 command 패턴(load→도메인 상태전이→save)을 미러링한다. 기존 `inquiries` 테이블/`InquiryEntity`를 재사용하고 컬럼 변경 없이 인덱스만 추가한다. 영속성은 `meeple-infra` 어댑터가 어드민 out-port를 구현한다.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4 / Spring Data JPA / QueryDSL / Kotest(E2E·유닛) + Testcontainers / RestAssured DSL.

## Global Constraints

- 응답은 항상 한국어. 커밋 메시지 형식 `<type>(admin): <설명>`, 마지막 줄 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- `meeple-admin`은 core에 의존하지 않는다. 공용 enum `InquiryCategory`/`InquiryStatus`(`com.org.meeple.common.inquiry`)만 사용한다.
- 타입 명시: 변수·반환·람다 파라미터 타입 생략 금지. 도메인 검증은 도메인 모델에 캡슐화(서비스에 `if…throw` 나열 금지). 현재 시각은 admin `TimeGenerator.now()` 주입으로 얻고 도메인엔 파라미터로 넘긴다.
- CQRS: 조회 서비스 `@Transactional(readOnly = true)` / 명령 서비스 `@Transactional`. 조회는 read model(DTO) 반환, 명령은 도메인 모델. 조회 dao와 명령 out-port를 섞지 않는다.
- 어드민 경로 `/admin/**`는 `SecurityConfig`의 `hasRole("ADMIN")`로 자동 보호(메서드 어노테이션 불필요). 어드민 예외는 `AdminException(AdminErrorCode, message)` → `AdminExceptionHandler`가 `success=false`, `error.code` 봉투로 변환.
- 페이징: `page`(default 0, `coerceAtLeast(0)`), `size`(default 20, `coerceIn(1, 100)`). offset/limit 직접 계산(Spring `Pageable` 미사용). 정렬 `created_at desc, id desc`.
- 빌드 검증 명령: `./gradlew :meeple-admin:compileKotlin`, 유닛 `./gradlew :meeple-api:test --tests "<FQCN>"`, E2E `./gradlew :meeple-api:test --tests "<FQCN>"`.

---

## 파일 구조 (생성/수정 목록)

**meeple-admin (`com.org.meeple.admin.inquiry`)**
- 생성 `query/dto/AdminInquiryView.kt` — 목록 행 read model
- 생성 `query/dto/AdminInquiryViews.kt` — 일급 컬렉션
- 생성 `query/dto/AdminInquiryDetailView.kt` — 상세 read model
- 생성 `query/dto/AdminInquiryPage.kt` — 페이지 read model(+파생값)
- 생성 `query/dao/GetAdminInquiryDao.kt` — 조회 out-port
- 생성 `query/service/port/in/GetAdminInquiriesUseCase.kt` — 조회 in-port
- 생성 `query/service/GetAdminInquiriesService.kt` — 조회 서비스
- 생성 `command/domain/AdminInquiry.kt` + `AnsweredInquiry` — 답변 도메인
- 생성 `command/application/port/in/AnswerInquiryUseCase.kt` — 답변 in-port
- 생성 `command/application/port/in/command/AnswerInquiryCommand.kt` — 커맨드
- 생성 `command/application/port/out/GetAdminInquiryPort.kt` — 로드 out-port
- 생성 `command/application/port/out/AnswerAdminInquiryPort.kt` — 저장 out-port
- 생성 `command/application/AnswerInquiryService.kt` — 답변 서비스
- 수정 `common/error/AdminErrorCode.kt` — `INQUIRY_NOT_FOUND`(404), `INQUIRY_ALREADY_ANSWERED`(409) 추가

**meeple-infra**
- 생성 `infra/inquiry/query/GetAdminInquiryDaoImpl.kt` — QueryDSL 조회 구현
- 수정 `infra/inquiry/command/adapter/InquiryAdapter.kt` — `GetAdminInquiryPort`+`AnswerAdminInquiryPort` 추가 구현
- 수정 `infra/inquiry/command/entity/InquiryEntity.kt` — 복합 인덱스 추가
- 생성 `src/testFixtures/.../infra/fixture/InquiryEntityFixture.kt` — 문의 픽스처

**meeple-api (`com.org.meeple.api.admin`)**
- 생성 `AdminInquiryController.kt` — 목록/상세/답변 엔드포인트
- 생성 `response/AdminInquiryResponse.kt` — 목록 행 응답
- 생성 `response/AdminInquiryPageResponse.kt` — 페이지 응답
- 생성 `response/AdminInquiryDetailResponse.kt` — 상세 응답
- 생성 `request/AnswerInquiryRequest.kt` — 답변 요청

**테스트 (meeple-api/src/test)**
- 생성 `domain/inquiry/AdminInquiryPageTest.kt` — 페이지 파생값 유닛
- 생성 `domain/inquiry/AdminInquiryTest.kt` — 답변 상태전이 유닛
- 생성 `api/admin/AdminInquiryE2ETest.kt` — 목록·필터·상세·답변 E2E

---

## Task 1: 조회 read model + 페이지 유닛 테스트

목록/상세 read model DTO와 페이지 파생값을 만들고, 페이지 계산을 유닛 테스트로 고정한다.

**Files:**
- Create: `meeple-admin/src/main/kotlin/com/org/meeple/admin/inquiry/query/dto/AdminInquiryView.kt`
- Create: `meeple-admin/src/main/kotlin/com/org/meeple/admin/inquiry/query/dto/AdminInquiryViews.kt`
- Create: `meeple-admin/src/main/kotlin/com/org/meeple/admin/inquiry/query/dto/AdminInquiryDetailView.kt`
- Create: `meeple-admin/src/main/kotlin/com/org/meeple/admin/inquiry/query/dto/AdminInquiryPage.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/domain/inquiry/AdminInquiryPageTest.kt`

**Interfaces:**
- Produces:
  - `AdminInquiryView(id: Long, category: InquiryCategory, status: InquiryStatus, email: String, createdAt: LocalDateTime?)`
  - `AdminInquiryViews(values: List<AdminInquiryView>)` + `companion object { fun empty(): AdminInquiryViews }`
  - `AdminInquiryDetailView(id: Long, userId: Long?, category: InquiryCategory, email: String, message: String, status: InquiryStatus, answer: String?, answeredAt: LocalDateTime?, createdAt: LocalDateTime?)`
  - `AdminInquiryPage(content: AdminInquiryViews, page: Int, size: Int, totalElements: Long)` with derived `totalPages: Int`, `hasNext: Boolean`, + `companion object { fun empty(page: Int, size: Int): AdminInquiryPage }`

- [ ] **Step 1: read model 4종 작성**

`AdminInquiryView.kt`:
```kotlin
package com.org.meeple.admin.inquiry.query.dto

import com.org.meeple.common.inquiry.InquiryCategory
import com.org.meeple.common.inquiry.InquiryStatus
import java.time.LocalDateTime

/** 어드민 문의 목록 한 건(read model). 본문(message)은 상세에서만 노출한다. */
data class AdminInquiryView(
	val id: Long,
	val category: InquiryCategory,
	val status: InquiryStatus,
	val email: String,
	val createdAt: LocalDateTime?,
)
```

`AdminInquiryViews.kt`:
```kotlin
package com.org.meeple.admin.inquiry.query.dto

/** 어드민 문의 목록 read model 일급 컬렉션. */
data class AdminInquiryViews(
	val values: List<AdminInquiryView>,
) {
	companion object {
		fun empty(): AdminInquiryViews = AdminInquiryViews(emptyList())
	}
}
```

`AdminInquiryDetailView.kt`:
```kotlin
package com.org.meeple.admin.inquiry.query.dto

import com.org.meeple.common.inquiry.InquiryCategory
import com.org.meeple.common.inquiry.InquiryStatus
import java.time.LocalDateTime

/** 어드민 문의 상세 read model. 목록 필드 + 본문(message)·답변(answer/answeredAt)·작성자(userId). */
data class AdminInquiryDetailView(
	val id: Long,
	val userId: Long?,
	val category: InquiryCategory,
	val email: String,
	val message: String,
	val status: InquiryStatus,
	val answer: String?,
	val answeredAt: LocalDateTime?,
	val createdAt: LocalDateTime?,
)
```

`AdminInquiryPage.kt`:
```kotlin
package com.org.meeple.admin.inquiry.query.dto

/**
 * 어드민 문의 목록 한 페이지(read model). offset(page·size) 페이징 결과.
 * [totalElements]는 (필터 적용·soft delete 제외) 전체 개수, [totalPages]/[hasNext]는 파생값.
 */
data class AdminInquiryPage(
	val content: AdminInquiryViews,
	val page: Int,
	val size: Int,
	val totalElements: Long,
) {
	val totalPages: Int
		get() = if (size <= 0) 0 else ((totalElements + size - 1) / size).toInt()

	val hasNext: Boolean
		get() = (page + 1).toLong() * size < totalElements

	companion object {
		fun empty(page: Int, size: Int): AdminInquiryPage =
			AdminInquiryPage(AdminInquiryViews.empty(), page, size, 0)
	}
}
```

- [ ] **Step 2: 페이지 유닛 테스트 작성**

`AdminInquiryPageTest.kt`:
```kotlin
package com.org.meeple.domain.inquiry

import com.org.meeple.admin.inquiry.query.dto.AdminInquiryPage
import com.org.meeple.admin.inquiry.query.dto.AdminInquiryView
import com.org.meeple.admin.inquiry.query.dto.AdminInquiryViews
import com.org.meeple.common.inquiry.InquiryCategory
import com.org.meeple.common.inquiry.InquiryStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [AdminInquiryPage] read model 유닛 테스트.
 * offset 페이징 메타데이터([AdminInquiryPage.totalPages]/[AdminInquiryPage.hasNext]) 계산을 검증한다.
 */
class AdminInquiryPageTest : DescribeSpec({

	fun view(id: Long): AdminInquiryView =
		AdminInquiryView(
			id = id,
			category = InquiryCategory.ETC,
			status = InquiryStatus.PENDING,
			email = "user$id@test.com",
			createdAt = null,
		)

	fun views(count: Int): AdminInquiryViews =
		AdminInquiryViews((1..count).map { view(it.toLong()) })

	describe("totalPages") {
		it("전체 개수를 size로 나눈 올림 값이다") {
			AdminInquiryPage(content = views(2), page = 0, size = 2, totalElements = 5).totalPages shouldBe 3
		}

		it("전체 개수가 size로 나누어떨어지면 그 몫이다") {
			AdminInquiryPage(content = views(2), page = 0, size = 2, totalElements = 4).totalPages shouldBe 2
		}

		it("전체가 0이면 0이다") {
			AdminInquiryPage.empty(page = 0, size = 20).totalPages shouldBe 0
		}
	}

	describe("hasNext") {
		it("뒤에 페이지가 더 있으면 true다") {
			AdminInquiryPage(content = views(2), page = 0, size = 2, totalElements = 5).hasNext shouldBe true
		}

		it("마지막(부분) 페이지면 false다") {
			AdminInquiryPage(content = views(1), page = 2, size = 2, totalElements = 5).hasNext shouldBe false
		}

		it("정확히 꽉 찬 마지막 페이지면 false다") {
			AdminInquiryPage(content = views(2), page = 1, size = 2, totalElements = 4).hasNext shouldBe false
		}
	}
})
```

- [ ] **Step 3: 테스트 실행 (통과 확인)**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.domain.inquiry.AdminInquiryPageTest"`
Expected: PASS (6 tests). read model 컴파일 + 파생값 계산 검증.

- [ ] **Step 4: 커밋**

```bash
git add meeple-admin/src/main/kotlin/com/org/meeple/admin/inquiry/query/dto meeple-api/src/test/kotlin/com/org/meeple/domain/inquiry/AdminInquiryPageTest.kt
git commit -m "$(cat <<'EOF'
feat(admin): 어드민 문의 조회 read model·페이지 파생값 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: 문의 목록·상세 조회 슬라이스 + E2E

조회 in-port/서비스/dao(out-port)/QueryDSL 구현/컨트롤러/응답 DTO를 만들고, 엔티티에 복합 인덱스와 픽스처를 추가한 뒤 목록·상세를 E2E로 검증한다.

**Files:**
- Create: `meeple-admin/src/main/kotlin/com/org/meeple/admin/inquiry/query/dao/GetAdminInquiryDao.kt`
- Create: `meeple-admin/src/main/kotlin/com/org/meeple/admin/inquiry/query/service/port/in/GetAdminInquiriesUseCase.kt`
- Create: `meeple-admin/src/main/kotlin/com/org/meeple/admin/inquiry/query/service/GetAdminInquiriesService.kt`
- Modify: `meeple-admin/src/main/kotlin/com/org/meeple/admin/common/error/AdminErrorCode.kt`
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/inquiry/query/GetAdminInquiryDaoImpl.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/inquiry/command/entity/InquiryEntity.kt`
- Create: `meeple-infra/src/testFixtures/kotlin/com/org/meeple/infra/fixture/InquiryEntityFixture.kt`
- Create: `meeple-api/src/main/kotlin/com/org/meeple/api/admin/AdminInquiryController.kt`
- Create: `meeple-api/src/main/kotlin/com/org/meeple/api/admin/response/AdminInquiryResponse.kt`
- Create: `meeple-api/src/main/kotlin/com/org/meeple/api/admin/response/AdminInquiryPageResponse.kt`
- Create: `meeple-api/src/main/kotlin/com/org/meeple/api/admin/response/AdminInquiryDetailResponse.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/api/admin/AdminInquiryE2ETest.kt`

**Interfaces:**
- Consumes (Task 1): `AdminInquiryView(s)`, `AdminInquiryDetailView`, `AdminInquiryPage`.
- Produces:
  - `GetAdminInquiryDao`: `findPage(offset: Long, limit: Int, status: InquiryStatus?): AdminInquiryViews`, `count(status: InquiryStatus?): Long`, `findDetailById(id: Long): AdminInquiryDetailView?`
  - `GetAdminInquiriesUseCase`: `getInquiries(page: Int, size: Int, status: InquiryStatus?): AdminInquiryPage`, `getInquiry(id: Long): AdminInquiryDetailView`
  - `AdminErrorCode.INQUIRY_NOT_FOUND`
  - `InquiryEntityFixture.create(userId, category, email, message, status, answer, answeredAt): InquiryEntity`

- [ ] **Step 1: 조회 out-port(dao) 작성**

`GetAdminInquiryDao.kt`:
```kotlin
package com.org.meeple.admin.inquiry.query.dao

import com.org.meeple.admin.inquiry.query.dto.AdminInquiryDetailView
import com.org.meeple.admin.inquiry.query.dto.AdminInquiryViews
import com.org.meeple.common.inquiry.InquiryStatus

/** 어드민 문의 조회 dao(query out-port). [status]가 null이면 전체, 있으면 해당 상태만. */
interface GetAdminInquiryDao {

	/** 문의를 최신순(created_at desc, id desc)으로 [offset]부터 [limit]건 조회한다. */
	fun findPage(offset: Long, limit: Int, status: InquiryStatus?): AdminInquiryViews

	/** (필터 적용·soft delete 제외) 전체 문의 개수. (페이징 메타데이터 계산용) */
	fun count(status: InquiryStatus?): Long

	/** 문의 상세를 [id]로 조회한다. 없거나 soft-delete면 null. */
	fun findDetailById(id: Long): AdminInquiryDetailView?
}
```

- [ ] **Step 2: 조회 in-port(UseCase) 작성**

`GetAdminInquiriesUseCase.kt`:
```kotlin
package com.org.meeple.admin.inquiry.query.service.port.`in`

import com.org.meeple.admin.inquiry.query.dto.AdminInquiryDetailView
import com.org.meeple.admin.inquiry.query.dto.AdminInquiryPage
import com.org.meeple.common.inquiry.InquiryStatus

/** 어드민 문의 조회 유스케이스. (조회 전용) */
interface GetAdminInquiriesUseCase {

	/** 문의를 최신순으로 page(0부터)·size 페이징 조회한다. [status]가 null이면 전체. */
	fun getInquiries(page: Int, size: Int, status: InquiryStatus?): AdminInquiryPage

	/** 문의 상세를 id로 조회한다. 없으면 INQUIRY_NOT_FOUND. */
	fun getInquiry(id: Long): AdminInquiryDetailView
}
```

- [ ] **Step 3: `AdminErrorCode`에 `INQUIRY_NOT_FOUND` 추가**

`AdminErrorCode.kt`의 마지막 enum 항목(`NOTICE_NOT_FOUND` 줄) 다음에 추가:
```kotlin
	NOTICE_NOT_FOUND("NOTICE-001", "공지를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	INQUIRY_NOT_FOUND("INQUIRY-001", "문의를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
```

- [ ] **Step 4: 조회 서비스 작성**

`GetAdminInquiriesService.kt`:
```kotlin
package com.org.meeple.admin.inquiry.query.service

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.inquiry.query.dao.GetAdminInquiryDao
import com.org.meeple.admin.inquiry.query.dto.AdminInquiryDetailView
import com.org.meeple.admin.inquiry.query.dto.AdminInquiryPage
import com.org.meeple.admin.inquiry.query.dto.AdminInquiryViews
import com.org.meeple.admin.inquiry.query.service.port.`in`.GetAdminInquiriesUseCase
import com.org.meeple.common.inquiry.InquiryStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetAdminInquiriesUseCase] 구현. (조회 전용 - 쓰기 부수효과 없음)
 * 문의를 접수 시각(created_at) 최신순으로 limit/offset(page·size) 페이징 조회하고,
 * 전체 개수를 함께 조회해 페이지 메타데이터([AdminInquiryPage])를 구성한다.
 */
@Service
@Transactional(readOnly = true)
class GetAdminInquiriesService(
	private val getAdminInquiryDao: GetAdminInquiryDao,
) : GetAdminInquiriesUseCase {

	override fun getInquiries(page: Int, size: Int, status: InquiryStatus?): AdminInquiryPage {
		val pageNumber: Int = page.coerceAtLeast(0)
		val pageSize: Int = size.coerceIn(1, MAX_PAGE_SIZE)
		val offset: Long = pageNumber.toLong() * pageSize
		val inquiries: AdminInquiryViews = getAdminInquiryDao.findPage(offset, pageSize, status)
		return AdminInquiryPage(
			content = inquiries,
			page = pageNumber,
			size = pageSize,
			totalElements = getAdminInquiryDao.count(status),
		)
	}

	override fun getInquiry(id: Long): AdminInquiryDetailView =
		getAdminInquiryDao.findDetailById(id)
			?: throw AdminException(AdminErrorCode.INQUIRY_NOT_FOUND, "문의를 찾을 수 없습니다: $id")

	companion object {
		private const val MAX_PAGE_SIZE: Int = 100
	}
}
```

- [ ] **Step 5: 엔티티에 복합 인덱스 추가**

`InquiryEntity.kt`의 `@Table` `indexes` 배열에 한 줄 추가:
```kotlin
	indexes = [
		Index(name = "idx_user_id", columnList = "user_id"),
		Index(name = "idx_inquiries_status_created_at", columnList = "status, created_at, id"),
	],
```
(status 동등 조건 → created_at·id 정렬을 seek로 받친다. 운영 DB에는 별도 DDL로 동일 인덱스를 반영해야 한다.)

- [ ] **Step 6: QueryDSL 조회 구현 작성**

`GetAdminInquiryDaoImpl.kt`:
```kotlin
package com.org.meeple.infra.inquiry.query

import com.org.meeple.admin.inquiry.query.dao.GetAdminInquiryDao
import com.org.meeple.admin.inquiry.query.dto.AdminInquiryDetailView
import com.org.meeple.admin.inquiry.query.dto.AdminInquiryView
import com.org.meeple.admin.inquiry.query.dto.AdminInquiryViews
import com.org.meeple.common.inquiry.InquiryStatus
import com.org.meeple.infra.inquiry.command.entity.QInquiryEntity
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetAdminInquiryDao]의 QueryDSL 구현. (조회 전용)
 * 문의를 접수 시각(created_at) 내림차순(동률이면 id 내림차순)으로 offset/limit 페이징해 read model에 직접 투영한다.
 * status가 주어지면 동등 필터를 적용한다. (soft delete 행은 @SQLRestriction으로 양쪽 쿼리에서 제외)
 * 저장/갱신 out-port는 [com.org.meeple.infra.inquiry.command.adapter.InquiryAdapter]가 따로 구현한다.
 */
@Component
class GetAdminInquiryDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetAdminInquiryDao {

	override fun findPage(offset: Long, limit: Int, status: InquiryStatus?): AdminInquiryViews {
		val inquiry: QInquiryEntity = QInquiryEntity.inquiryEntity
		val views: List<AdminInquiryView> = queryFactory
			.select(
				Projections.constructor(
					AdminInquiryView::class.java,
					inquiry.id,
					inquiry.category,
					inquiry.status,
					inquiry.email,
					inquiry.createdAt,
				),
			)
			.from(inquiry)
			.where(statusEq(inquiry, status))
			.orderBy(inquiry.createdAt.desc(), inquiry.id.desc())
			.offset(offset)
			.limit(limit.toLong())
			.fetch()
		return AdminInquiryViews(views)
	}

	override fun count(status: InquiryStatus?): Long {
		val inquiry: QInquiryEntity = QInquiryEntity.inquiryEntity
		return queryFactory
			.select(inquiry.count())
			.from(inquiry)
			.where(statusEq(inquiry, status))
			.fetchOne() ?: 0L
	}

	override fun findDetailById(id: Long): AdminInquiryDetailView? {
		val inquiry: QInquiryEntity = QInquiryEntity.inquiryEntity
		return queryFactory
			.select(
				Projections.constructor(
					AdminInquiryDetailView::class.java,
					inquiry.id,
					inquiry.userId,
					inquiry.category,
					inquiry.email,
					inquiry.message,
					inquiry.status,
					inquiry.answer,
					inquiry.answeredAt,
					inquiry.createdAt,
				),
			)
			.from(inquiry)
			.where(inquiry.id.eq(id))
			.fetchOne()
	}

	// status가 null이면 null을 반환 → QueryDSL where가 해당 조건을 무시(전체 조회).
	private fun statusEq(inquiry: QInquiryEntity, status: InquiryStatus?): BooleanExpression? =
		status?.let { inquiry.status.eq(it) }
}
```

- [ ] **Step 7: 문의 픽스처 작성**

`InquiryEntityFixture.kt`:
```kotlin
package com.org.meeple.infra.fixture

import com.org.meeple.common.inquiry.InquiryCategory
import com.org.meeple.common.inquiry.InquiryStatus
import com.org.meeple.infra.inquiry.command.entity.InquiryEntity
import java.time.LocalDateTime

/**
 * [InquiryEntity] 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다.
 * 접수 시각(created_at)은 저장 시 JPA Auditing이 채운다.
 */
object InquiryEntityFixture {

	fun create(
		userId: Long? = null,
		category: InquiryCategory = InquiryCategory.ETC,
		email: String = "user@test.com",
		message: String = "문의 내용",
		status: InquiryStatus = InquiryStatus.PENDING,
		answer: String? = null,
		answeredAt: LocalDateTime? = null,
	): InquiryEntity =
		InquiryEntity(
			userId = userId,
			category = category,
			email = email,
			message = message,
			status = status,
			answer = answer,
			answeredAt = answeredAt,
		)
}
```

- [ ] **Step 8: 응답 DTO 3종 작성**

`AdminInquiryResponse.kt`:
```kotlin
package com.org.meeple.api.admin.response

import com.org.meeple.admin.inquiry.query.dto.AdminInquiryView
import com.org.meeple.admin.inquiry.query.dto.AdminInquiryViews
import com.org.meeple.common.inquiry.InquiryCategory
import com.org.meeple.common.inquiry.InquiryStatus
import java.time.LocalDateTime

/** 어드민 문의 목록 항목 응답. 본문(message)은 상세에서만 노출한다. */
data class AdminInquiryResponse(
	val id: Long,
	val category: InquiryCategory,
	val status: InquiryStatus,
	val email: String,
	val createdAt: LocalDateTime?,
) {
	companion object {
		private fun of(view: AdminInquiryView): AdminInquiryResponse =
			AdminInquiryResponse(
				id = view.id,
				category = view.category,
				status = view.status,
				email = view.email,
				createdAt = view.createdAt,
			)

		fun listOf(views: AdminInquiryViews): List<AdminInquiryResponse> =
			views.values.map { view: AdminInquiryView -> of(view) }
	}
}
```

`AdminInquiryPageResponse.kt`:
```kotlin
package com.org.meeple.api.admin.response

import com.org.meeple.admin.inquiry.query.dto.AdminInquiryPage

/** 어드민 문의 목록 페이지 응답. (offset 페이징) */
data class AdminInquiryPageResponse(
	val content: List<AdminInquiryResponse>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
	val hasNext: Boolean,
) {
	companion object {
		fun of(page: AdminInquiryPage): AdminInquiryPageResponse =
			AdminInquiryPageResponse(
				content = AdminInquiryResponse.listOf(page.content),
				page = page.page,
				size = page.size,
				totalElements = page.totalElements,
				totalPages = page.totalPages,
				hasNext = page.hasNext,
			)
	}
}
```

`AdminInquiryDetailResponse.kt`:
```kotlin
package com.org.meeple.api.admin.response

import com.org.meeple.admin.inquiry.query.dto.AdminInquiryDetailView
import com.org.meeple.common.inquiry.InquiryCategory
import com.org.meeple.common.inquiry.InquiryStatus
import java.time.LocalDateTime

/** 어드민 문의 상세 응답. 목록 필드 + 본문(message)·답변(answer/answeredAt)·작성자(userId). */
data class AdminInquiryDetailResponse(
	val id: Long,
	val userId: Long?,
	val category: InquiryCategory,
	val email: String,
	val message: String,
	val status: InquiryStatus,
	val answer: String?,
	val answeredAt: LocalDateTime?,
	val createdAt: LocalDateTime?,
) {
	companion object {
		fun of(view: AdminInquiryDetailView): AdminInquiryDetailResponse =
			AdminInquiryDetailResponse(
				id = view.id,
				userId = view.userId,
				category = view.category,
				email = view.email,
				message = view.message,
				status = view.status,
				answer = view.answer,
				answeredAt = view.answeredAt,
				createdAt = view.createdAt,
			)
	}
}
```

- [ ] **Step 9: 컨트롤러 작성 (목록·상세)**

`AdminInquiryController.kt`:
```kotlin
package com.org.meeple.api.admin

import com.org.meeple.admin.inquiry.query.service.port.`in`.GetAdminInquiriesUseCase
import com.org.meeple.api.admin.response.AdminInquiryDetailResponse
import com.org.meeple.api.admin.response.AdminInquiryPageResponse
import com.org.meeple.common.inquiry.InquiryStatus
import com.org.meeple.core.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 어드민 문의 엔드포인트. `/admin` 하위는 SecurityConfig의 hasRole(ADMIN)으로 보호된다.
 * - GET /: 최신순 page·size 페이징 목록(본문 제외). status로 상태 필터.
 * - GET /{id}: 상세(본문·답변 포함). 없으면 404(INQUIRY-001).
 */
@Tag(name = "어드민 문의", description = "어드민 백오피스 문의 조회·답변. ROLE_ADMIN 토큰만 접근할 수 있다.")
@RestController
@RequestMapping("/admin/v1/inquiries")
class AdminInquiryController(
	private val getAdminInquiriesUseCase: GetAdminInquiriesUseCase,
) {

	@Operation(
		summary = "문의 목록 조회",
		description = "문의를 접수 시각 최신순으로 page(0부터)·size 페이징 조회한다. status(PENDING/ANSWERED)로 상태를 필터할 수 있다. 목록 항목은 본문(message)을 포함하지 않는다.",
	)
	@GetMapping
	fun inquiries(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@RequestParam(required = false) status: InquiryStatus?,
	): ApiResponse<AdminInquiryPageResponse> =
		ApiResponse.success(AdminInquiryPageResponse.of(getAdminInquiriesUseCase.getInquiries(page, size, status)))

	@Operation(
		summary = "문의 상세 조회",
		description = "문의 한 건을 id로 조회한다(본문·답변 포함). 없으면 404(INQUIRY-001).",
	)
	@GetMapping("/{id}")
	fun inquiry(
		@PathVariable id: Long,
	): ApiResponse<AdminInquiryDetailResponse> =
		ApiResponse.success(AdminInquiryDetailResponse.of(getAdminInquiriesUseCase.getInquiry(id)))
}
```

- [ ] **Step 10: 목록·상세 E2E 테스트 작성**

`AdminInquiryE2ETest.kt`:
```kotlin
package com.org.meeple.api.admin

import com.org.meeple.common.inquiry.InquiryCategory
import com.org.meeple.common.inquiry.InquiryStatus
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.infra.fixture.InquiryEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.inquiry.command.entity.QInquiryEntity
import org.hamcrest.Matchers.hasSize

/**
 * 어드민 문의 API E2E 테스트.
 * - GET /admin/v1/inquiries: 최신순 페이징, status 필터. 목록 행은 id/category/status/email/createdAt만.
 * - GET /admin/v1/inquiries/{id}: 상세(본문·답변 포함), 없는 id 404(INQUIRY-001).
 */
class AdminInquiryE2ETest : AbstractIntegrationSupport({

	describe("GET /admin/v1/inquiries") {

		it("최신순으로 페이징 조회하고 목록 행은 본문(message)을 제외한다") {
			IntegrationUtil.persist(
				InquiryEntityFixture.create(category = InquiryCategory.ACCOUNT, email = "a@test.com", message = "본문1"),
			)
			val lastId: Long = IntegrationUtil.persist(
				InquiryEntityFixture.create(category = InquiryCategory.PAYMENT, email = "b@test.com", message = "본문2"),
			).id!!

			get("/admin/v1/inquiries") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("success", true)
				body("data.totalElements", 2)
				body("data.content", hasSize<Any>(2))
				body("data.content[0].id", lastId.toInt())
				body("data.content[0].category", "PAYMENT")
				body("data.content[0].status", "PENDING")
				body("data.content[0].email", "b@test.com")
				// 목록 행에 본문(message)은 없다.
				body("data.content[0].message", null)
			}
		}

		it("status로 상태를 필터한다") {
			IntegrationUtil.persist(InquiryEntityFixture.create(status = InquiryStatus.PENDING))
			IntegrationUtil.persist(InquiryEntityFixture.create(status = InquiryStatus.ANSWERED, answer = "답변함"))

			get("/admin/v1/inquiries?status=ANSWERED") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.totalElements", 1)
				body("data.content", hasSize<Any>(1))
				body("data.content[0].status", "ANSWERED")
			}
		}

		it("size로 페이지 크기를 제한한다") {
			(1..3).forEach { index: Int ->
				IntegrationUtil.persist(InquiryEntityFixture.create(message = "문의-$index"))
			}

			get("/admin/v1/inquiries?page=0&size=2") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.content", hasSize<Any>(2))
				body("data.size", 2)
				body("data.totalElements", 3)
				body("data.totalPages", 2)
				body("data.hasNext", true)
			}
		}
	}

	describe("GET /admin/v1/inquiries/{id}") {

		it("문의 상세를 본문·답변과 함께 반환한다 (200)") {
			val id: Long = IntegrationUtil.persist(
				InquiryEntityFixture.create(
					userId = 42L,
					category = InquiryCategory.MATCHING,
					email = "detail@test.com",
					message = "상세 본문",
				),
			).id!!

			get("/admin/v1/inquiries/$id") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.id", id.toInt())
				body("data.userId", 42)
				body("data.category", "MATCHING")
				body("data.email", "detail@test.com")
				body("data.message", "상세 본문")
				body("data.status", "PENDING")
				body("data.answer", null)
			}
		}

		it("없는 id면 404다 (INQUIRY-001)") {
			get("/admin/v1/inquiries/999999") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(404)
				body("success", false)
				body("error.code", "INQUIRY-001")
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QInquiryEntity.inquiryEntity)
	}
})
```

- [ ] **Step 11: 컴파일 확인**

Run: `./gradlew :meeple-admin:compileKotlin :meeple-infra:compileKotlin :meeple-api:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 12: E2E 실행 (통과 확인)**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.admin.AdminInquiryE2ETest"`
Expected: PASS (5 tests: 목록 최신순·본문제외, status 필터, size 페이징, 상세, 없는 id 404).

- [ ] **Step 13: 커밋**

```bash
git add meeple-admin meeple-infra meeple-api
git commit -m "$(cat <<'EOF'
feat(admin): 어드민 문의 목록·상세 조회 API 및 상태 필터·인덱스 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: 답변 도메인 + 상태전이 유닛 테스트

답변 도메인 모델과 상태전이 규칙(PENDING만 답변, 재답변 불허)을 만들고 유닛 테스트로 고정한다.

**Files:**
- Create: `meeple-admin/src/main/kotlin/com/org/meeple/admin/inquiry/command/domain/AdminInquiry.kt`
- Modify: `meeple-admin/src/main/kotlin/com/org/meeple/admin/common/error/AdminErrorCode.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/domain/inquiry/AdminInquiryTest.kt`

**Interfaces:**
- Produces:
  - `AdminInquiry(id: Long, status: InquiryStatus)` with `fun answer(content: String, now: LocalDateTime): AnsweredInquiry`
  - `AnsweredInquiry(id: Long, answer: String, answeredAt: LocalDateTime)`
  - `AdminErrorCode.INQUIRY_ALREADY_ANSWERED`

- [ ] **Step 1: `AdminErrorCode`에 `INQUIRY_ALREADY_ANSWERED` 추가**

`INQUIRY_NOT_FOUND` 줄 다음에 추가:
```kotlin
	INQUIRY_NOT_FOUND("INQUIRY-001", "문의를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	INQUIRY_ALREADY_ANSWERED("INQUIRY-002", "이미 답변된 문의입니다.", HttpStatus.CONFLICT),
```

- [ ] **Step 2: 답변 상태전이 유닛 테스트 작성**

`AdminInquiryTest.kt`:
```kotlin
package com.org.meeple.domain.inquiry

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.inquiry.command.domain.AdminInquiry
import com.org.meeple.common.inquiry.InquiryStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [AdminInquiry] 답변 도메인 유닛 테스트.
 * PENDING만 답변 가능(재답변 불허) 규칙을 검증한다.
 */
class AdminInquiryTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 7, 5, 10, 0)

	describe("answer") {
		it("PENDING이면 답변 값을 만든다") {
			val inquiry = AdminInquiry(id = 7L, status = InquiryStatus.PENDING)

			val answered = inquiry.answer(content = "답변 내용", now = now)

			answered.id shouldBe 7L
			answered.answer shouldBe "답변 내용"
			answered.answeredAt shouldBe now
		}

		it("이미 답변된(ANSWERED) 문의면 INQUIRY_ALREADY_ANSWERED를 던진다") {
			val inquiry = AdminInquiry(id = 7L, status = InquiryStatus.ANSWERED)

			val exception = shouldThrow<AdminException> {
				inquiry.answer(content = "답변 내용", now = now)
			}
			exception.errorCode shouldBe AdminErrorCode.INQUIRY_ALREADY_ANSWERED
		}
	}
})
```

- [ ] **Step 3: 테스트 실행 (실패 확인)**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.domain.inquiry.AdminInquiryTest"`
Expected: FAIL — `AdminInquiry`/`AnsweredInquiry` 미정의(컴파일 에러).

- [ ] **Step 4: 답변 도메인 작성**

`AdminInquiry.kt`:
```kotlin
package com.org.meeple.admin.inquiry.command.domain

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.common.inquiry.InquiryStatus
import java.time.LocalDateTime

/**
 * 어드민 문의 답변 도메인 모델(명령 측). 답변 가능 여부 판정에 필요한 id·status만 담는다.
 * (admin은 core에 의존하지 않으므로 core Inquiry를 쓰지 않고 자체 최소 모델을 둔다)
 */
data class AdminInquiry(
	val id: Long,
	val status: InquiryStatus,
) {
	/**
	 * 문의에 답변한다. PENDING이 아니면(이미 답변됨) INQUIRY_ALREADY_ANSWERED.
	 * 통과 시 저장할 답변 값([AnsweredInquiry])을 만든다. (재답변 불허 규칙을 도메인에 캡슐화)
	 */
	fun answer(content: String, now: LocalDateTime): AnsweredInquiry {
		if (status != InquiryStatus.PENDING) {
			throw AdminException(AdminErrorCode.INQUIRY_ALREADY_ANSWERED, "이미 답변된 문의입니다: $id")
		}
		return AnsweredInquiry(id = id, answer = content, answeredAt = now)
	}
}

/** 답변 저장 값. 상태는 저장 시 ANSWERED로 전이한다. */
data class AnsweredInquiry(
	val id: Long,
	val answer: String,
	val answeredAt: LocalDateTime,
)
```

- [ ] **Step 5: 테스트 실행 (통과 확인)**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.domain.inquiry.AdminInquiryTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: 커밋**

```bash
git add meeple-admin/src/main/kotlin/com/org/meeple/admin/inquiry/command/domain meeple-admin/src/main/kotlin/com/org/meeple/admin/common/error/AdminErrorCode.kt meeple-api/src/test/kotlin/com/org/meeple/domain/inquiry/AdminInquiryTest.kt
git commit -m "$(cat <<'EOF'
feat(admin): 어드민 문의 답변 도메인·재답변 불허 규칙 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: 답변 슬라이스 + E2E

답변 in-port/커맨드/out-port/서비스/어댑터 구현/컨트롤러 답변 엔드포인트/요청 DTO를 만들고 답변 성공·재답변 409·미존재 404·입력 400을 E2E로 검증한다.

**Files:**
- Create: `meeple-admin/src/main/kotlin/com/org/meeple/admin/inquiry/command/application/port/in/AnswerInquiryUseCase.kt`
- Create: `meeple-admin/src/main/kotlin/com/org/meeple/admin/inquiry/command/application/port/in/command/AnswerInquiryCommand.kt`
- Create: `meeple-admin/src/main/kotlin/com/org/meeple/admin/inquiry/command/application/port/out/GetAdminInquiryPort.kt`
- Create: `meeple-admin/src/main/kotlin/com/org/meeple/admin/inquiry/command/application/port/out/AnswerAdminInquiryPort.kt`
- Create: `meeple-admin/src/main/kotlin/com/org/meeple/admin/inquiry/command/application/AnswerInquiryService.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/inquiry/command/adapter/InquiryAdapter.kt`
- Create: `meeple-api/src/main/kotlin/com/org/meeple/api/admin/request/AnswerInquiryRequest.kt`
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/api/admin/AdminInquiryController.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/api/admin/AdminInquiryE2ETest.kt` (답변 describe 추가)

**Interfaces:**
- Consumes (Task 3): `AdminInquiry`, `AnsweredInquiry`, `AdminErrorCode.INQUIRY_ALREADY_ANSWERED`. (Task 2) `AdminErrorCode.INQUIRY_NOT_FOUND`. admin `TimeGenerator`(`com.org.meeple.admin.common.time.TimeGenerator`).
- Produces:
  - `AnswerInquiryUseCase.answer(command: AnswerInquiryCommand)`
  - `AnswerInquiryCommand(inquiryId: Long, answer: String)`
  - `GetAdminInquiryPort.findById(id: Long): AdminInquiry?`
  - `AnswerAdminInquiryPort.answer(answered: AnsweredInquiry)`

- [ ] **Step 1: 답변 in-port·커맨드 작성**

`AnswerInquiryCommand.kt`:
```kotlin
package com.org.meeple.admin.inquiry.command.application.port.`in`.command

/** 어드민 문의 답변 커맨드. */
data class AnswerInquiryCommand(
	val inquiryId: Long,
	val answer: String,
)
```

`AnswerInquiryUseCase.kt`:
```kotlin
package com.org.meeple.admin.inquiry.command.application.port.`in`

import com.org.meeple.admin.inquiry.command.application.port.`in`.command.AnswerInquiryCommand

/** 어드민 문의 답변 유스케이스. (명령) */
interface AnswerInquiryUseCase {

	/** 문의에 답변한다. 없으면 INQUIRY_NOT_FOUND, 이미 답변됐으면 INQUIRY_ALREADY_ANSWERED. */
	fun answer(command: AnswerInquiryCommand)
}
```

- [ ] **Step 2: 답변 out-port 2종 작성**

`GetAdminInquiryPort.kt`:
```kotlin
package com.org.meeple.admin.inquiry.command.application.port.out

import com.org.meeple.admin.inquiry.command.domain.AdminInquiry

/** 답변 대상 문의 로드 out-port. 없거나 soft-delete면 null. */
fun interface GetAdminInquiryPort {
	fun findById(id: Long): AdminInquiry?
}
```

`AnswerAdminInquiryPort.kt`:
```kotlin
package com.org.meeple.admin.inquiry.command.application.port.out

import com.org.meeple.admin.inquiry.command.domain.AnsweredInquiry

/** 문의 답변 저장 out-port. answer/answered_at 저장 + status=ANSWERED 전이. infra 어댑터가 구현한다. */
fun interface AnswerAdminInquiryPort {
	fun answer(answered: AnsweredInquiry)
}
```

- [ ] **Step 3: 답변 서비스 작성**

`AnswerInquiryService.kt`:
```kotlin
package com.org.meeple.admin.inquiry.command.application

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.common.time.TimeGenerator
import com.org.meeple.admin.inquiry.command.application.port.`in`.AnswerInquiryUseCase
import com.org.meeple.admin.inquiry.command.application.port.`in`.command.AnswerInquiryCommand
import com.org.meeple.admin.inquiry.command.application.port.out.AnswerAdminInquiryPort
import com.org.meeple.admin.inquiry.command.application.port.out.GetAdminInquiryPort
import com.org.meeple.admin.inquiry.command.domain.AdminInquiry
import com.org.meeple.admin.inquiry.command.domain.AnsweredInquiry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [AnswerInquiryUseCase] 구현. (명령)
 * 대상 문의를 로드해 없으면 INQUIRY_NOT_FOUND. 답변 가능 여부(PENDING)는 도메인이 판정하고,
 * 통과 시 답변 값을 저장(answer/answered_at + status=ANSWERED)한다. 답변 시각은 TimeGenerator로 얻는다.
 */
@Service
@Transactional
class AnswerInquiryService(
	private val getAdminInquiryPort: GetAdminInquiryPort,
	private val answerAdminInquiryPort: AnswerAdminInquiryPort,
	private val timeGenerator: TimeGenerator,
) : AnswerInquiryUseCase {

	override fun answer(command: AnswerInquiryCommand) {
		val inquiry: AdminInquiry = getAdminInquiryPort.findById(command.inquiryId)
			?: throw AdminException(AdminErrorCode.INQUIRY_NOT_FOUND, "문의를 찾을 수 없습니다: ${command.inquiryId}")
		val answered: AnsweredInquiry = inquiry.answer(command.answer, timeGenerator.now())
		answerAdminInquiryPort.answer(answered)
	}
}
```

- [ ] **Step 4: `InquiryAdapter`에 로드·답변 저장 구현 추가**

`InquiryAdapter.kt` 전체를 아래로 교체(기존 `SaveInquiryPort` 구현 유지 + 어드민 포트 2종 추가):
```kotlin
package com.org.meeple.infra.inquiry.command.adapter

import com.org.meeple.admin.inquiry.command.application.port.out.AnswerAdminInquiryPort
import com.org.meeple.admin.inquiry.command.application.port.out.GetAdminInquiryPort
import com.org.meeple.admin.inquiry.command.domain.AdminInquiry
import com.org.meeple.admin.inquiry.command.domain.AnsweredInquiry
import com.org.meeple.common.inquiry.InquiryStatus
import com.org.meeple.core.inquiry.command.application.port.out.SaveInquiryPort
import com.org.meeple.core.inquiry.command.domain.Inquiry
import com.org.meeple.infra.inquiry.command.entity.InquiryEntity
import com.org.meeple.infra.inquiry.command.mapper.toDomain
import com.org.meeple.infra.inquiry.command.mapper.toEntity
import com.org.meeple.infra.inquiry.command.repository.InquiryJpaRepository
import org.springframework.stereotype.Component

/**
 * [InquiryEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 유저용 저장 out-port([SaveInquiryPort])와 어드민 로드·답변 out-port([GetAdminInquiryPort]·[AnswerAdminInquiryPort])를 함께 구현한다.
 * 어드민 조회는 [com.org.meeple.infra.inquiry.query.GetAdminInquiryDaoImpl]가 따로 담당한다.
 */
@Component
class InquiryAdapter(
	private val inquiryJpaRepository: InquiryJpaRepository,
) : SaveInquiryPort, GetAdminInquiryPort, AnswerAdminInquiryPort {

	override fun save(inquiry: Inquiry): Inquiry =
		inquiryJpaRepository.save(inquiry.toEntity()).toDomain()

	override fun findById(id: Long): AdminInquiry? =
		inquiryJpaRepository.findById(id)
			.map { entity: InquiryEntity -> AdminInquiry(id = entity.id ?: 0, status = entity.status) }
			.orElse(null)

	// 기존 행을 로드해 answer/answered_at을 채우고 status를 ANSWERED로 전이해 저장한다. (다른 필드 보존)
	override fun answer(answered: AnsweredInquiry) {
		val entity: InquiryEntity = inquiryJpaRepository.findById(answered.id)
			.orElseThrow { IllegalStateException("문의를 찾을 수 없습니다: ${answered.id}") }
		entity.answer = answered.answer
		entity.answeredAt = answered.answeredAt
		entity.status = InquiryStatus.ANSWERED
		inquiryJpaRepository.save(entity)
	}
}
```

- [ ] **Step 5: 답변 요청 DTO 작성**

`AnswerInquiryRequest.kt`:
```kotlin
package com.org.meeple.api.admin.request

import com.org.meeple.admin.inquiry.command.application.port.`in`.command.AnswerInquiryCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AnswerInquiryRequest(
	@field:NotBlank(message = "답변 내용은 필수입니다.")
	@field:Size(max = 2000, message = "답변 내용은 2000자 이하여야 합니다.")
	val answer: String? = null,
) {
	// @Valid 통과 후 호출 → answer non-null/non-blank 보장 → command로 변환
	fun toCommand(inquiryId: Long): AnswerInquiryCommand =
		AnswerInquiryCommand(
			inquiryId = inquiryId,
			answer = answer!!,
		)
}
```

- [ ] **Step 6: 컨트롤러에 답변 엔드포인트 추가**

`AdminInquiryController.kt`를 수정한다. 생성자에 `AnswerInquiryUseCase` 주입 추가, 답변 매핑 추가, import 추가.

생성자 변경:
```kotlin
class AdminInquiryController(
	private val getAdminInquiriesUseCase: GetAdminInquiriesUseCase,
	private val answerInquiryUseCase: AnswerInquiryUseCase,
) {
```

`inquiry(...)` 상세 메서드 다음에 추가:
```kotlin
	@Operation(
		summary = "문의 답변",
		description = "PENDING 문의에 답변한다(status=ANSWERED 전이). 없으면 404(INQUIRY-001), 이미 답변됐으면 409(INQUIRY-002), 답변이 비면 400.",
	)
	@PostMapping("/{id}/answer")
	fun answer(
		@PathVariable id: Long,
		@RequestBody @Valid request: AnswerInquiryRequest,
	): ApiResponse<Unit> {
		answerInquiryUseCase.answer(request.toCommand(id))
		return ApiResponse.success()
	}
```

추가 import(파일 상단 import 블록에):
```kotlin
import com.org.meeple.admin.inquiry.command.application.port.`in`.AnswerInquiryUseCase
import com.org.meeple.api.admin.request.AnswerInquiryRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
```

- [ ] **Step 7: 답변 E2E 테스트 추가**

`AdminInquiryE2ETest.kt`의 `describe("GET /admin/v1/inquiries/{id}")` 블록 다음(그리고 `afterTest` 앞)에 아래 describe를 추가한다. `post`/`put` import도 상단에 추가한다.

상단 import 추가:
```kotlin
import com.org.meeple.common.integration.post
```

추가 describe 블록:
```kotlin
	describe("POST /admin/v1/inquiries/{id}/answer") {

		it("PENDING 문의에 답변하면 상세가 ANSWERED로 바뀐다 (200)") {
			val id: Long = IntegrationUtil.persist(
				InquiryEntityFixture.create(message = "답변 대상", status = InquiryStatus.PENDING),
			).id!!

			post("/admin/v1/inquiries/$id/answer") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"answer":"안녕하세요, 확인 후 답변드립니다."}""")
			} expect {
				status(200)
				body("success", true)
			}

			get("/admin/v1/inquiries/$id") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.status", "ANSWERED")
				body("data.answer", "안녕하세요, 확인 후 답변드립니다.")
				body("data.answeredAt", org.hamcrest.Matchers.notNullValue())
			}
		}

		it("이미 답변된 문의면 409다 (INQUIRY-002)") {
			val id: Long = IntegrationUtil.persist(
				InquiryEntityFixture.create(status = InquiryStatus.ANSWERED, answer = "기존 답변"),
			).id!!

			post("/admin/v1/inquiries/$id/answer") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"answer":"두 번째 답변"}""")
			} expect {
				status(409)
				body("success", false)
				body("error.code", "INQUIRY-002")
			}
		}

		it("없는 id면 404다 (INQUIRY-001)") {
			post("/admin/v1/inquiries/999999/answer") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"answer":"답변"}""")
			} expect {
				status(404)
				body("error.code", "INQUIRY-001")
			}
		}

		it("답변이 비면 400이다") {
			val id: Long = IntegrationUtil.persist(InquiryEntityFixture.create()).id!!

			post("/admin/v1/inquiries/$id/answer") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"answer":""}""")
			} expect {
				status(400)
			}
		}
	}
```

- [ ] **Step 8: 컴파일 확인**

Run: `./gradlew :meeple-admin:compileKotlin :meeple-infra:compileKotlin :meeple-api:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: 전체 E2E 실행 (통과 확인)**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.admin.AdminInquiryE2ETest"`
Expected: PASS (9 tests: 조회 5 + 답변 4).

- [ ] **Step 10: 커밋**

```bash
git add meeple-admin meeple-infra meeple-api
git commit -m "$(cat <<'EOF'
feat(admin): 어드민 문의 답변 API 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: 전체 회귀 검증

**Files:** (없음 — 검증만)

- [ ] **Step 1: 문의 도메인 유닛·E2E 전체 실행**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.domain.inquiry.*" --tests "com.org.meeple.api.admin.AdminInquiryE2ETest"`
Expected: PASS (Page 6 + Domain 2 + E2E 9 = 17 tests).

- [ ] **Step 2: 어드민·인프라 컴파일 회귀 확인**

Run: `./gradlew :meeple-admin:compileKotlin :meeple-infra:compileKotlin :meeple-api:compileKotlin :meeple-api:compileTestKotlin`
Expected: BUILD SUCCESSFUL. (인접 모듈에 영향 없음)

---

## Self-Review (스펙 대비 점검)

- **엔드포인트 3종**: 목록(Task 2 Step 9)·상세(Task 2 Step 9)·답변(Task 4 Step 6) ✅
- **status 선택 필터**: dao/service/컨트롤러 nullable status (Task 2), E2E 필터 케이스 ✅
- **목록 message 제외**: `AdminInquiryView`에 message 없음, E2E `content[0].message` null ✅
- **재답변 불허(409)**: 도메인 `answer()` 검증(Task 3), E2E INQUIRY-002 ✅
- **미존재 404**: 상세·답변 INQUIRY-001 ✅
- **알림/이메일 미발송**: 답변 서비스는 저장만, 발송 포트 없음 ✅
- **테이블 재사용·인덱스 추가**: 컬럼 변경 없음, `idx_inquiries_status_created_at` 추가(Task 2 Step 5). 운영 DB DDL 반영 필요(주석 명시) ✅
- **CQRS/시각 주입/도메인 검증 캡슐화**: 조회 readOnly·명령 @Transactional 분리, `TimeGenerator.now()` 주입, 상태검증 도메인 캡슐화 ✅
- **테스트**: `AdminInquiryPageTest`·`AdminInquiryTest`(유닛), `AdminInquiryE2ETest`(E2E) ✅
- **타입 일관성**: `findPage/count(status)`, `getInquiries(page,size,status)`, `answer(command)`, `GetAdminInquiryPort.findById`, `AnswerAdminInquiryPort.answer(AnsweredInquiry)` — 태스크 간 시그니처 일치 확인 ✅

## 주의: 운영 DB DDL

`idx_inquiries_status_created_at (status, created_at, id)` 인덱스는 엔티티 선언과 별개로 운영 MySQL에 수동/마이그레이션으로 반영해야 한다(스키마 자동 생성이 아닌 환경 전제).
