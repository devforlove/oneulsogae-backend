# 어드민 회사 이미지 인증 목록 조회 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 어드민이 사용자가 제출한 직장 서류 이미지 인증(`company_image_verifications`)을 페이징·status 필터로 조회하고, 각 항목의 서류를 S3 presigned GET URL로 열람할 수 있는 조회 API를 추가한다.

**Architecture:** 기존 `GET /admin/v1/reports`(어드민 신고 조회) 슬라이스를 미러링한다. `oneulsogae-admin`(core 비의존 자립 모듈)에 query 슬라이스(DTO/UseCase/Service/out-port)를 두고, `oneulsogae-infra`가 QueryDSL daoImpl과 S3 presign 어댑터로 out-port를 구현한다. `oneulsogae-api`에 컨트롤러·응답 DTO를 둔다.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4 / Spring Data JPA / QueryDSL / AWS SDK v2 (S3Presigner) / Kotest E2E(Testcontainers).

## Global Constraints

- **`oneulsogae-admin`은 `oneulsogae-core`를 의존하지 않는다.** admin 소스에 `com.org.oneulsogae.core.*` import 0건, `build.gradle`에 `project(":oneulsogae-core")` 미추가.
- **타입 명시**: 변수·반환 타입·람다 파라미터 타입을 생략하지 않는다.
- **CQRS**: 조회 전용. 서비스 `@Transactional(readOnly = true)`, 부수효과 없음.
- **응답 언어 한국어**, 커밋 메시지 형식 `<type>(admin): <설명>`.
- **정렬은 `id desc`** (PK 클러스터 인덱스 커버, 신규 인덱스 추가 안 함).
- 커밋 트레일러: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

## 참고: 미러 대상 기존 코드

- admin query 슬라이스 예: `oneulsogae-admin/.../admin/report/query/{dto,dao,service,service/port/in}`
- infra QueryDSL daoImpl 예: `oneulsogae-infra/.../infra/report/query/GetAdminReportDaoImpl.kt`
- api 컨트롤러/응답 예: `oneulsogae-api/.../api/admin/AdminReportController.kt`, `.../response/AdminReport*Response.kt`
- 엔티티: `CompanyImageVerificationEntity`(필드 `userId`, `imageKey`, `status`; `BaseEntity`에 `id`/`createdAt`/soft delete). Q타입: `QCompanyImageVerificationEntity.companyImageVerificationEntity`.
- 조인 대상: `QUserDetailEntity.userDetailEntity`(`userId`, `nickname`), `QUserEntity.userEntity`(`id`, `email`).
- 상태 enum: `com.org.oneulsogae.common.user.CompanyImageVerificationStatus`(`PENDING`/`APPROVED`/`REJECTED`, `.description` 한글 라벨).

---

## Task 1: oneulsogae-admin query 슬라이스 (DTO·UseCase·Service·out-port)

**Files:**
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/companyverification/query/dto/AdminCompanyVerificationView.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/companyverification/query/dto/AdminCompanyVerificationViews.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/companyverification/query/dto/AdminCompanyVerificationPage.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/companyverification/query/dao/GetAdminCompanyVerificationDao.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/companyverification/query/service/port/in/GetAdminCompanyVerificationsUseCase.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/companyverification/query/service/port/out/CompanyVerificationImageUrlPort.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/companyverification/query/service/GetAdminCompanyVerificationsService.kt`

**Interfaces:**
- Produces:
  - `AdminCompanyVerificationView(id: Long, userId: Long, nickname: String?, email: String?, status: CompanyImageVerificationStatus, createdAt: LocalDateTime?, imageKey: String, imageUrl: String?)` + 7-arg 보조 생성자(imageUrl 제외).
  - `AdminCompanyVerificationViews(values: List<AdminCompanyVerificationView>)`
  - `AdminCompanyVerificationPage(content: AdminCompanyVerificationViews, page: Int, size: Int, totalElements: Long)` (파생 `totalPages`, `hasNext`)
  - `GetAdminCompanyVerificationDao.findPage(offset: Long, limit: Int, status: CompanyImageVerificationStatus?): AdminCompanyVerificationViews`, `count(status: CompanyImageVerificationStatus?): Long`
  - `CompanyVerificationImageUrlPort.presignedGetUrl(imageKey: String): String`
  - `GetAdminCompanyVerificationsUseCase.getVerifications(page: Int, size: Int, status: CompanyImageVerificationStatus?): AdminCompanyVerificationPage`

- [ ] **Step 1: read model DTO 3종 작성**

`AdminCompanyVerificationView.kt`:

```kotlin
package com.org.oneulsogae.admin.companyverification.query.dto

import com.org.oneulsogae.common.user.CompanyImageVerificationStatus
import java.time.LocalDateTime

/**
 * 어드민 회사 이미지 인증 목록 한 건(read model).
 * dao는 [imageKey]까지 채우고 [imageUrl]은 null로 둔다. 서비스가 presign 결과로 [imageUrl]을 채운다.
 * (QueryDSL Projections.constructor가 imageUrl 없이 투영할 수 있도록 7-arg 보조 생성자를 둔다)
 */
data class AdminCompanyVerificationView(
	val id: Long,
	val userId: Long,
	val nickname: String?,
	val email: String?,
	val status: CompanyImageVerificationStatus,
	val createdAt: LocalDateTime?,
	val imageKey: String,
	val imageUrl: String? = null,
) {
	/** dao 투영용 생성자. imageUrl은 서비스가 presign으로 채운다. */
	constructor(
		id: Long,
		userId: Long,
		nickname: String?,
		email: String?,
		status: CompanyImageVerificationStatus,
		createdAt: LocalDateTime?,
		imageKey: String,
	) : this(id, userId, nickname, email, status, createdAt, imageKey, null)
}
```

`AdminCompanyVerificationViews.kt`:

```kotlin
package com.org.oneulsogae.admin.companyverification.query.dto

/** 어드민 회사 이미지 인증 목록 read model 일급 컬렉션. */
data class AdminCompanyVerificationViews(
	val values: List<AdminCompanyVerificationView>,
) {
	companion object {
		fun empty(): AdminCompanyVerificationViews = AdminCompanyVerificationViews(emptyList())
	}
}
```

`AdminCompanyVerificationPage.kt`:

```kotlin
package com.org.oneulsogae.admin.companyverification.query.dto

/**
 * 어드민 회사 이미지 인증 목록 한 페이지(read model). offset(page·size) 페이징 결과.
 * [totalElements]는 (status 필터 반영) 전체 개수, [totalPages]/[hasNext]는 파생값.
 */
data class AdminCompanyVerificationPage(
	val content: AdminCompanyVerificationViews,
	val page: Int,
	val size: Int,
	val totalElements: Long,
) {
	val totalPages: Int
		get() = if (size <= 0) 0 else ((totalElements + size - 1) / size).toInt()

	val hasNext: Boolean
		get() = (page + 1).toLong() * size < totalElements

	companion object {
		fun empty(page: Int, size: Int): AdminCompanyVerificationPage =
			AdminCompanyVerificationPage(AdminCompanyVerificationViews.empty(), page, size, 0)
	}
}
```

- [ ] **Step 2: dao(query out-port) 작성**

`GetAdminCompanyVerificationDao.kt`:

```kotlin
package com.org.oneulsogae.admin.companyverification.query.dao

import com.org.oneulsogae.admin.companyverification.query.dto.AdminCompanyVerificationViews
import com.org.oneulsogae.common.user.CompanyImageVerificationStatus

/** 어드민 회사 이미지 인증 조회 dao(query out-port). */
interface GetAdminCompanyVerificationDao {

	/** [status](없으면 전체)를 최신순(id desc)으로 [offset]부터 [limit]건 조회한다. */
	fun findPage(offset: Long, limit: Int, status: CompanyImageVerificationStatus?): AdminCompanyVerificationViews

	/** (soft delete 제외) [status](없으면 전체) 조건 전체 개수. (페이징 메타데이터 계산용) */
	fun count(status: CompanyImageVerificationStatus?): Long
}
```

- [ ] **Step 3: in-port(UseCase)·out-port(presign) 작성**

`service/port/in/GetAdminCompanyVerificationsUseCase.kt`:

```kotlin
package com.org.oneulsogae.admin.companyverification.query.service.port.`in`

import com.org.oneulsogae.admin.companyverification.query.dto.AdminCompanyVerificationPage
import com.org.oneulsogae.common.user.CompanyImageVerificationStatus

/** 어드민 회사 이미지 인증 목록 조회 유스케이스. */
interface GetAdminCompanyVerificationsUseCase {

	/** 회사 이미지 인증을 최신순으로 [page](0부터)·[size] 단위 페이징 조회한다. [status] 생략 시 전체. */
	fun getVerifications(page: Int, size: Int, status: CompanyImageVerificationStatus?): AdminCompanyVerificationPage
}
```

`service/port/out/CompanyVerificationImageUrlPort.kt`:

```kotlin
package com.org.oneulsogae.admin.companyverification.query.service.port.out

/**
 * 서류 이미지의 열람용 URL 발급 out-port. (S3 presigned GET URL)
 * admin은 인터페이스만 소유하고, infra가 S3Presigner로 구현한다.
 */
fun interface CompanyVerificationImageUrlPort {

	/** [imageKey](S3 오브젝트 키)에 대한, 일정 시간 유효한 열람용 URL을 발급한다. */
	fun presignedGetUrl(imageKey: String): String
}
```

- [ ] **Step 4: 조회 서비스 작성**

`service/GetAdminCompanyVerificationsService.kt`:

```kotlin
package com.org.oneulsogae.admin.companyverification.query.service

import com.org.oneulsogae.admin.companyverification.query.dao.GetAdminCompanyVerificationDao
import com.org.oneulsogae.admin.companyverification.query.dto.AdminCompanyVerificationPage
import com.org.oneulsogae.admin.companyverification.query.dto.AdminCompanyVerificationView
import com.org.oneulsogae.admin.companyverification.query.dto.AdminCompanyVerificationViews
import com.org.oneulsogae.admin.companyverification.query.service.port.`in`.GetAdminCompanyVerificationsUseCase
import com.org.oneulsogae.admin.companyverification.query.service.port.out.CompanyVerificationImageUrlPort
import com.org.oneulsogae.common.user.CompanyImageVerificationStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetAdminCompanyVerificationsUseCase] 구현. (조회 전용)
 * 회사 이미지 인증을 최신순 페이징 조회한 뒤, 각 행의 imageKey를 presigned 열람 URL로 변환해 반환한다.
 */
@Service
@Transactional(readOnly = true)
class GetAdminCompanyVerificationsService(
	private val getAdminCompanyVerificationDao: GetAdminCompanyVerificationDao,
	private val companyVerificationImageUrlPort: CompanyVerificationImageUrlPort,
) : GetAdminCompanyVerificationsUseCase {

	override fun getVerifications(
		page: Int,
		size: Int,
		status: CompanyImageVerificationStatus?,
	): AdminCompanyVerificationPage {
		val pageNumber: Int = page.coerceAtLeast(0)
		val pageSize: Int = size.coerceIn(1, MAX_PAGE_SIZE)
		val offset: Long = pageNumber.toLong() * pageSize

		val rows: AdminCompanyVerificationViews =
			getAdminCompanyVerificationDao.findPage(offset, pageSize, status)
		val withUrls: List<AdminCompanyVerificationView> = rows.values.map { view: AdminCompanyVerificationView ->
			view.copy(imageUrl = companyVerificationImageUrlPort.presignedGetUrl(view.imageKey))
		}

		return AdminCompanyVerificationPage(
			content = AdminCompanyVerificationViews(withUrls),
			page = pageNumber,
			size = pageSize,
			totalElements = getAdminCompanyVerificationDao.count(status),
		)
	}

	companion object {
		private const val MAX_PAGE_SIZE: Int = 100
	}
}
```

- [ ] **Step 5: 컴파일 확인 + core 비의존 확인**

Run: `./gradlew :oneulsogae-admin:compileKotlin -q`
Expected: 성공(출력 없음, exit 0). (infra/api는 아직 미구현이라 전체 빌드는 Task 3 후 성공)

Run: `grep -rn "com.org.oneulsogae.core" oneulsogae-admin/src --include="*.kt" | wc -l | tr -d ' '`
Expected: `0`

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/companyverification
git commit -m "feat(admin): 회사 이미지 인증 목록 조회 query 슬라이스 추가

DTO(View/Views/Page)·UseCase·조회 dao·presign out-port·조회 서비스.
서비스가 dao 조회 후 imageKey를 presigned URL로 변환해 Page를 조립한다.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: oneulsogae-infra — QueryDSL daoImpl + S3 presign 어댑터

**Files:**
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/config/S3Properties.kt` (만료 프로퍼티 추가)
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/config/S3Config.kt` (`S3Presigner` 빈 추가)
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/query/GetAdminCompanyVerificationDaoImpl.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/query/S3CompanyVerificationImageUrlAdapter.kt`

**Interfaces:**
- Consumes (Task 1): `GetAdminCompanyVerificationDao`, `CompanyVerificationImageUrlPort`, `AdminCompanyVerificationView`(7-arg 생성자), `AdminCompanyVerificationViews`.
- Produces: `@Component GetAdminCompanyVerificationDaoImpl`, `@Component S3CompanyVerificationImageUrlAdapter`, `@Bean S3Presigner`, `S3Properties.presignedGetExpiryMinutes: Long`.

- [ ] **Step 1: `S3Properties`에 만료 프로퍼티 추가**

`S3Properties.kt`의 `secretKey` 필드 뒤(마지막 프로퍼티 뒤)에 추가:

```kotlin
	/** 정적 시크릿 키. [accessKey]와 함께 비우면 기본 자격증명 체인을 쓴다. */
	val secretKey: String = "",
	/** presigned GET URL 서명 유효시간(분). 어드민 서류 열람용. */
	val presignedGetExpiryMinutes: Long = 10,
```

(기존 `secretKey` 줄의 끝 콤마 뒤에 새 줄을 추가한다.)

- [ ] **Step 2: `S3Config`에 `S3Presigner` 빈 추가**

`S3Config.kt` 상단 import에 추가:

```kotlin
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
```

`s3Client()` 빈 메서드 아래, `credentialsProvider()` 위에 추가:

```kotlin
	@Bean(destroyMethod = "close")
	fun s3Presigner(): S3Presigner {
		val builder: S3Presigner.Builder = S3Presigner.builder()
			.region(Region.of(properties.region))
			.credentialsProvider(credentialsProvider())
			.serviceConfiguration(
				S3Configuration.builder()
					.pathStyleAccessEnabled(properties.pathStyleAccess)
					.build(),
			)

		if (properties.endpoint.isNotBlank()) {
			builder.endpointOverride(URI.create(properties.endpoint))
		}
		return builder.build()
	}
```

- [ ] **Step 3: QueryDSL daoImpl 작성**

`GetAdminCompanyVerificationDaoImpl.kt`:

```kotlin
package com.org.oneulsogae.infra.user.query

import com.org.oneulsogae.admin.companyverification.query.dao.GetAdminCompanyVerificationDao
import com.org.oneulsogae.admin.companyverification.query.dto.AdminCompanyVerificationView
import com.org.oneulsogae.admin.companyverification.query.dto.AdminCompanyVerificationViews
import com.org.oneulsogae.common.user.CompanyImageVerificationStatus
import com.org.oneulsogae.infra.user.command.entity.QCompanyImageVerificationEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetAdminCompanyVerificationDao]의 QueryDSL 구현. (조회 전용)
 * company_image_verifications를 최신순(id desc)으로 페이징하고,
 * user_details(nickname)·users(email)를 userId로 leftJoin해 표시 정보를 채운다.
 * (@SQLRestriction으로 soft-delete 행은 자동 제외. status 필터는 있으면 동등 조건, 없으면 전체)
 */
@Component
class GetAdminCompanyVerificationDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetAdminCompanyVerificationDao {

	override fun findPage(
		offset: Long,
		limit: Int,
		status: CompanyImageVerificationStatus?,
	): AdminCompanyVerificationViews {
		val verification: QCompanyImageVerificationEntity = QCompanyImageVerificationEntity.companyImageVerificationEntity
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val user: QUserEntity = QUserEntity.userEntity

		val views: List<AdminCompanyVerificationView> = queryFactory
			.select(
				Projections.constructor(
					AdminCompanyVerificationView::class.java,
					verification.id,
					verification.userId,
					detail.nickname,
					user.email,
					verification.status,
					verification.createdAt,
					verification.imageKey,
				),
			)
			.from(verification)
			.leftJoin(detail).on(detail.userId.eq(verification.userId))
			.leftJoin(user).on(user.id.eq(verification.userId))
			.where(statusEq(verification, status))
			.orderBy(verification.id.desc())
			.offset(offset)
			.limit(limit.toLong())
			.fetch()
		return AdminCompanyVerificationViews(views)
	}

	override fun count(status: CompanyImageVerificationStatus?): Long {
		val verification: QCompanyImageVerificationEntity = QCompanyImageVerificationEntity.companyImageVerificationEntity
		return queryFactory
			.select(verification.count())
			.from(verification)
			.where(statusEq(verification, status))
			.fetchOne() ?: 0L
	}

	/** status가 있으면 동등 조건, 없으면 null(=where 무시). */
	private fun statusEq(
		verification: QCompanyImageVerificationEntity,
		status: CompanyImageVerificationStatus?,
	): BooleanExpression? =
		status?.let { verification.status.eq(it) }
}
```

- [ ] **Step 4: S3 presign 어댑터 작성**

`S3CompanyVerificationImageUrlAdapter.kt`:

```kotlin
package com.org.oneulsogae.infra.user.query

import com.org.oneulsogae.admin.companyverification.query.service.port.out.CompanyVerificationImageUrlPort
import com.org.oneulsogae.infra.config.S3Properties
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.time.Duration

/**
 * [CompanyVerificationImageUrlPort]의 S3 구현. 비공개 버킷의 오브젝트에 대해
 * 일정 시간([S3Properties.presignedGetExpiryMinutes]) 유효한 presigned GET URL을 발급한다.
 * (서명은 로컬에서 이뤄져 S3 네트워크 왕복이 없다)
 */
@Component
class S3CompanyVerificationImageUrlAdapter(
	private val s3Presigner: S3Presigner,
	private val s3Properties: S3Properties,
) : CompanyVerificationImageUrlPort {

	override fun presignedGetUrl(imageKey: String): String {
		val getObjectRequest: GetObjectRequest = GetObjectRequest.builder()
			.bucket(s3Properties.bucket)
			.key(imageKey)
			.build()
		val presignRequest: GetObjectPresignRequest = GetObjectPresignRequest.builder()
			.signatureDuration(Duration.ofMinutes(s3Properties.presignedGetExpiryMinutes))
			.getObjectRequest(getObjectRequest)
			.build()
		return s3Presigner.presignGetObject(presignRequest).url().toString()
	}
}
```

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew :oneulsogae-infra:compileKotlin -q`
Expected: 성공(exit 0).

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/config/S3Properties.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/config/S3Config.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/query
git commit -m "feat(admin): 회사 이미지 인증 조회 QueryDSL daoImpl·S3 presign 어댑터 추가

daoImpl은 user_details·users를 조인해 최신순 페이징하고 status 옵션 필터를 건다.
S3CompanyVerificationImageUrlAdapter는 S3Presigner로 열람용 presigned GET URL을 발급한다.
S3Config에 S3Presigner 빈, S3Properties에 만료 프로퍼티를 추가한다.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: oneulsogae-api — 컨트롤러 + 응답 DTO

**Files:**
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/response/AdminCompanyVerificationResponse.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/response/AdminCompanyVerificationPageResponse.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/AdminCompanyVerificationController.kt`

**Interfaces:**
- Consumes (Task 1): `GetAdminCompanyVerificationsUseCase.getVerifications(page, size, status)`, `AdminCompanyVerificationPage`, `AdminCompanyVerificationView`, `CompanyImageVerificationStatus`.
- Produces: `GET /admin/v1/company-image-verifications` 엔드포인트, `AdminCompanyVerificationResponse.of(view)`, `AdminCompanyVerificationPageResponse.of(page)`.

- [ ] **Step 1: 목록 항목 응답 DTO 작성**

`response/AdminCompanyVerificationResponse.kt`:

```kotlin
package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.companyverification.query.dto.AdminCompanyVerificationView
import java.time.LocalDateTime

/**
 * 어드민 회사 이미지 인증 목록 항목 응답. status는 코드(name)와 한글 라벨(description)을 함께 노출한다.
 * 서류는 오브젝트 키 대신 열람용 presigned URL([imageUrl])만 노출한다.
 */
data class AdminCompanyVerificationResponse(
	val id: Long,
	val userId: Long,
	val status: String,
	val statusLabel: String,
	val createdAt: LocalDateTime?,
	val nickname: String?,
	val email: String?,
	val imageUrl: String?,
) {
	companion object {
		fun of(view: AdminCompanyVerificationView): AdminCompanyVerificationResponse =
			AdminCompanyVerificationResponse(
				id = view.id,
				userId = view.userId,
				status = view.status.name,
				statusLabel = view.status.description,
				createdAt = view.createdAt,
				nickname = view.nickname,
				email = view.email,
				imageUrl = view.imageUrl,
			)
	}
}
```

- [ ] **Step 2: 페이지 응답 DTO 작성**

`response/AdminCompanyVerificationPageResponse.kt`:

```kotlin
package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.companyverification.query.dto.AdminCompanyVerificationPage

/** 어드민 회사 이미지 인증 목록 페이지 응답. (offset 페이징) */
data class AdminCompanyVerificationPageResponse(
	val content: List<AdminCompanyVerificationResponse>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
	val hasNext: Boolean,
) {
	companion object {
		fun of(page: AdminCompanyVerificationPage): AdminCompanyVerificationPageResponse =
			AdminCompanyVerificationPageResponse(
				content = page.content.values.map(AdminCompanyVerificationResponse::of),
				page = page.page,
				size = page.size,
				totalElements = page.totalElements,
				totalPages = page.totalPages,
				hasNext = page.hasNext,
			)
	}
}
```

- [ ] **Step 3: 컨트롤러 작성**

`AdminCompanyVerificationController.kt`:

```kotlin
package com.org.oneulsogae.api.admin

import com.org.oneulsogae.admin.companyverification.query.service.port.`in`.GetAdminCompanyVerificationsUseCase
import com.org.oneulsogae.api.admin.response.AdminCompanyVerificationPageResponse
import com.org.oneulsogae.common.user.CompanyImageVerificationStatus
import com.org.oneulsogae.core.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 어드민 회사 이미지 인증 조회 엔드포인트. `/admin` 하위는 SecurityConfig의 hasRole(ADMIN)으로 보호된다.
 */
@Tag(name = "어드민 회사 인증", description = "어드민 백오피스 직장 서류 인증 조회. ROLE_ADMIN 토큰만 접근할 수 있다.")
@RestController
@RequestMapping("/admin/v1/company-image-verifications")
class AdminCompanyVerificationController(
	private val getAdminCompanyVerificationsUseCase: GetAdminCompanyVerificationsUseCase,
) {

	@Operation(
		summary = "회사 이미지 인증 목록 조회",
		description = "직장 서류 인증을 최신순으로 page(0부터)·size 페이징 조회한다. status(PENDING/APPROVED/REJECTED) 생략 시 전체. 각 항목의 imageUrl은 일정 시간 유효한 열람용 presigned URL이다.",
	)
	@GetMapping
	fun verifications(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@RequestParam(required = false) status: CompanyImageVerificationStatus?,
	): ApiResponse<AdminCompanyVerificationPageResponse> =
		ApiResponse.success(
			AdminCompanyVerificationPageResponse.of(
				getAdminCompanyVerificationsUseCase.getVerifications(page, size, status),
			),
		)
}
```

- [ ] **Step 4: 전체 컴파일 확인**

Run: `./gradlew :oneulsogae-api:compileKotlin -q`
Expected: 성공(exit 0).

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/response/AdminCompanyVerificationResponse.kt \
        oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/response/AdminCompanyVerificationPageResponse.kt \
        oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/AdminCompanyVerificationController.kt
git commit -m "feat(admin): 회사 이미지 인증 목록 조회 컨트롤러·응답 DTO 추가

GET /admin/v1/company-image-verifications (page·size·status). status는 enum 파라미터.
응답은 status 코드+라벨·조인된 nickname/email·열람용 imageUrl을 노출한다.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: E2E 테스트 + 테스트용 presign 페이크

**Files:**
- Modify: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/common/config/TestFileStorageConfig.kt` (presign 페이크 빈 추가)
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/admin/AdminCompanyVerificationListE2ETest.kt`

**Interfaces:**
- Consumes: `GET /admin/v1/company-image-verifications`, `CompanyVerificationImageUrlPort`(페이크 대상), `CompanyImageVerificationEntityFixture.create(userId, imageKey, status)`, `UserEntityFixture`, `UserDetailEntityFixture`, `IntegrationUtil`, `adminAccessTokenFor(id)`.

- [ ] **Step 1: 테스트용 presign 페이크 빈 추가**

`TestFileStorageConfig.kt`를 아래로 수정한다. (KDoc에 presign 페이크 설명을 더하고, 인메모리 페이크 빈을 추가)

```kotlin
package com.org.oneulsogae.common.config

import com.org.oneulsogae.admin.companyverification.query.service.port.out.CompanyVerificationImageUrlPort
import com.org.oneulsogae.core.user.command.application.port.out.FileStoragePort
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * 통합 테스트에서 S3 관련 out-port를 인메모리 페이크로 대체한다.
 * - [FileStoragePort](업로드): 넘어온 key를 그대로 돌려준다.
 * - [CompanyVerificationImageUrlPort](presign): imageKey로 결정적 URL을 만든다.
 * E2E 컨텍스트에는 실제 S3(LocalStack)를 띄우지 않으므로 컨트롤러→서비스→DB 슬라이스만 검증한다.
 * 실제 S3 업로드/서명은 각 어댑터의 LocalStack 통합 테스트에서 따로 검증한다.
 * [AbstractIntegrationSupport]에 등록돼 모든 통합 테스트가 단일 컨텍스트를 공유한다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestFileStorageConfig {

	@Bean
	@Primary
	fun fakeFileStoragePort(): FileStoragePort =
		FileStoragePort { key: String, _: ByteArray, _: String -> key }

	@Bean
	@Primary
	fun fakeCompanyVerificationImageUrlPort(): CompanyVerificationImageUrlPort =
		CompanyVerificationImageUrlPort { imageKey: String -> "https://presigned.test/$imageKey" }
}
```

- [ ] **Step 2: E2E 테스트 작성 (실패 확인용)**

`AdminCompanyVerificationListE2ETest.kt`:

```kotlin
package com.org.oneulsogae.api.admin

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.user.CompanyImageVerificationStatus
import com.org.oneulsogae.infra.fixture.CompanyImageVerificationEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.user.command.entity.QCompanyImageVerificationEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import org.hamcrest.Matchers.hasSize

/**
 * `GET /admin/v1/company-image-verifications` E2E 테스트.
 * 직장 서류 인증을 최신순(id desc) 페이징 조회하고, status 필터·페이징 메타데이터,
 * user_details(nickname)·users(email) 조인, 열람용 imageUrl(테스트 페이크)이 채워지는지 검증한다.
 * (presigned URL은 TestFileStorageConfig의 페이크로 대체 — https://presigned.test/<imageKey>)
 */
class AdminCompanyVerificationListE2ETest : AbstractIntegrationSupport({

	describe("GET /admin/v1/company-image-verifications") {

		it("최신순으로 페이징 조회하고 조인 정보·imageUrl을 채운다") {
			val userId: Long = IntegrationUtil.persist(
				UserEntityFixture.create(providerId = "civ-list", email = "civ@test.com"),
			).id!!
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, nickname = "인증유저"))
			IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(
					userId = userId,
					imageKey = "key-pending",
					status = CompanyImageVerificationStatus.PENDING,
				),
			)
			IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(
					userId = userId,
					imageKey = "key-approved",
					status = CompanyImageVerificationStatus.APPROVED,
				),
			)

			get("/admin/v1/company-image-verifications") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("success", true)
				body("data.totalElements", 2)
				body("data.content", hasSize<Any>(2))
				// 최신순(id desc): 마지막에 저장한 APPROVED가 먼저.
				body("data.content[0].status", "APPROVED")
				body("data.content[0].statusLabel", "승인")
				body("data.content[0].imageUrl", "https://presigned.test/key-approved")
				body("data.content[0].userId", userId.toInt())
				body("data.content[0].nickname", "인증유저")
				body("data.content[0].email", "civ@test.com")
			}
		}

		it("status로 필터한다") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "civ-filter")).id!!
			IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(userId = userId, imageKey = "f-pending", status = CompanyImageVerificationStatus.PENDING),
			)
			IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(userId = userId, imageKey = "f-approved", status = CompanyImageVerificationStatus.APPROVED),
			)
			IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(userId = userId, imageKey = "f-rejected", status = CompanyImageVerificationStatus.REJECTED),
			)

			get("/admin/v1/company-image-verifications?status=PENDING") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.totalElements", 1)
				body("data.content", hasSize<Any>(1))
				body("data.content[0].status", "PENDING")
				body("data.content[0].statusLabel", "심사 대기")
			}
		}

		it("size로 페이지 크기를 제한한다") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "civ-page")).id!!
			(1..3).forEach { index: Int ->
				IntegrationUtil.persist(
					CompanyImageVerificationEntityFixture.create(userId = userId, imageKey = "p-$index"),
				)
			}

			get("/admin/v1/company-image-verifications?page=0&size=2") {
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

	afterTest {
		IntegrationUtil.deleteAll(QCompanyImageVerificationEntity.companyImageVerificationEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
```

- [ ] **Step 3: E2E 실행**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.admin.AdminCompanyVerificationListE2ETest" -q`
Expected: PASS (3개 케이스 모두 통과). Task 1~3에서 구현이 완료됐으므로 통과해야 한다. 실패 시 메시지에 따라 원인(조인·정렬·필터·페이크 URL)을 수정한다.

- [ ] **Step 4: 전체 빌드·테스트 확인**

Run: `./gradlew build -q`
Expected: 성공(exit 0). (기존 테스트 회귀 없음)

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-api/src/test/kotlin/com/org/oneulsogae/common/config/TestFileStorageConfig.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/admin/AdminCompanyVerificationListE2ETest.kt
git commit -m "test(admin): 회사 이미지 인증 목록 조회 E2E 추가

최신순 페이징·status 필터·페이징 메타데이터·조인 필드·presigned imageUrl을 검증한다.
TestFileStorageConfig에 CompanyVerificationImageUrlPort 인메모리 페이크를 추가한다.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review 결과

- **Spec coverage**: 엔드포인트(Task 3)·status 필터(Task 1 dao/service, 3 컨트롤러, 4 테스트)·presigned URL(Task 1 out-port, 2 어댑터/빈, 4 페이크)·페이징(Task 1 Page, 4 테스트)·admin core 비의존(Task 1 Step 5)·인덱스 미추가(설계 결정, 코드 변경 없음) 모두 태스크로 커버됨.
- **Placeholder scan**: 모든 코드 스텝에 실제 코드 포함, TODO/TBD 없음.
- **Type consistency**: `getVerifications(page, size, status)` / `findPage(offset, limit, status)` / `count(status)` / `presignedGetUrl(imageKey)` / `AdminCompanyVerificationView`(8-arg 본 생성자 + 7-arg 보조 생성자) / `AdminCompanyVerificationPage.content` 명칭이 Task 1~4에서 일치. Projections는 7-arg 보조 생성자에 바인딩.
