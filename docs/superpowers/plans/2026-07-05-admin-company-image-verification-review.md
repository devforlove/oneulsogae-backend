# 어드민 회사 이미지 인증 승인/반려 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 어드민이 직장 서류 이미지 인증을 승인(회사명 기입 → 유저 companyName 확정 + status=APPROVED) 또는 반려(status=REJECTED)하는 API를 추가한다.

**Architecture:** `oneulsogae-admin`의 첫 command 슬라이스(`companyverification/command`). admin은 core 비의존이라 자체 도메인(`AdminCompanyImageVerification`)·command out-port를 두고, infra의 기존 어댑터가 그 포트를 추가 구현한다(엔티티당 어댑터 하나, core/admin 동명 포트는 import alias). 범위는 최소(회사명+상태만) — 가입 상태 전환·코인·추천 없음.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4 / Spring Data JPA / QueryDSL / Kotest E2E(Testcontainers).

## Global Constraints

- **`oneulsogae-admin`은 `oneulsogae-core`를 의존하지 않는다.** admin 소스에 `com.org.oneulsogae.core.*` import 0건.
- **타입 명시**: 변수·반환 타입·람다 파라미터 타입 생략 금지.
- **CQRS**: 명령 서비스 `@Transactional`(readOnly 아님).
- **에러코드**: 없는 id → 기존 `AdminErrorCode.COMPANY_IMAGE_VERIFICATION_NOT_FOUND`("COMPANY-IMAGE-001", NOT_FOUND) 재사용.
- **엔티티당 어댑터 하나**: 새 어댑터를 만들지 말고 기존 어댑터에 admin 포트를 추가 구현한다. core/admin 동명 `SaveCompanyImageVerificationPort`는 **import alias**로 구분.
- **상태 가드 없음**: 현재 상태와 무관하게 approve/reject 허용(어드민 오버라이드).
- **범위 최소**: 승인은 companyName 확정 + status만. 가입상태·코인·추천 없음. companyEmail 미변경. 반려는 status만.
- 응답 언어 한국어. 커밋 트레일러: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

## 참고: 기존 코드

- `AdminCompanyVerificationController`(api): `@RequestMapping("/admin/v1/company-image-verifications")`, 생성자에 `getAdminCompanyVerificationsUseCase` 주입, GET 목록/상세 2개 보유. (여기에 approve/reject POST 추가 + UseCase 주입 추가)
- `AdminErrorCode.COMPANY_IMAGE_VERIFICATION_NOT_FOUND` 이미 존재.
- infra `CompanyImageVerificationRepositoryAdapter`: `CompanyImageVerificationJpaRepository`(JpaRepository, `findById` 제공) 주입, core `SaveCompanyImageVerificationPort` 구현 중. 엔티티 `CompanyImageVerificationEntity`(BaseEntity `id: Long?`, `var status`, `val userId`, `val imageKey`).
- infra `UserDetailCoreAdapter`: `UserDetailJpaRepository`(`findByUserId(userId): UserDetailEntity?`) 주입, core user detail 포트 구현 중. 엔티티 `UserDetailEntity`(`var companyName`).
- infra는 이미 `implementation(project(":oneulsogae-admin"))` 의존.
- 요청 DTO 검증 관례: `@field:NotBlank(message="...") val x: String? = null` + 컨트롤러 `@RequestBody @Valid`.
- E2E DSL: `post("/p") { bearer(token); jsonBody("""{...}""") } expect { status(200); body(...) }`. DB 재조회: `IntegrationUtil.getQuery().selectFrom(Q).where(...).fetchOne()`. 픽스처: `UserEntityFixture`, `UserDetailEntityFixture`, `CompanyImageVerificationEntityFixture`. 어드민 토큰: `adminAccessTokenFor(9901L)`.

---

## Task 1: oneulsogae-admin — command 슬라이스 (도메인·UseCase·Service·out-port)

**Files:**
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/companyverification/command/domain/AdminCompanyImageVerification.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/companyverification/command/application/port/in/ReviewCompanyImageVerificationUseCase.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/companyverification/command/application/port/out/GetCompanyImageVerificationPort.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/companyverification/command/application/port/out/SaveCompanyImageVerificationPort.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/companyverification/command/application/port/out/UpdateUserCompanyNamePort.kt`
- Create: `oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/companyverification/command/application/ReviewCompanyImageVerificationService.kt`

**Interfaces:**
- Produces:
  - `AdminCompanyImageVerification(id: Long, userId: Long, status: CompanyImageVerificationStatus)` + `approve()`, `reject()`.
  - `ReviewCompanyImageVerificationUseCase.approve(id: Long, companyName: String)`, `reject(id: Long)`.
  - `GetCompanyImageVerificationPort.findById(id: Long): AdminCompanyImageVerification?`
  - `SaveCompanyImageVerificationPort.save(verification: AdminCompanyImageVerification): AdminCompanyImageVerification`
  - `UpdateUserCompanyNamePort.updateCompanyName(userId: Long, companyName: String)`

- [ ] **Step 1: 도메인 모델 작성**

`command/domain/AdminCompanyImageVerification.kt`:

```kotlin
package com.org.oneulsogae.admin.companyverification.command.domain

import com.org.oneulsogae.common.user.CompanyImageVerificationStatus

/**
 * 어드민 심사용 회사 이미지 인증 도메인 모델(최소). 상태 전이(승인/반려)를 캡슐화한다.
 * (admin은 core에 의존하지 않으므로 core CompanyImageVerification을 쓰지 않고 심사에 필요한 최소 필드만 둔다)
 */
data class AdminCompanyImageVerification(
	val id: Long,
	val userId: Long,
	val status: CompanyImageVerificationStatus,
) {
	fun approve(): AdminCompanyImageVerification = copy(status = CompanyImageVerificationStatus.APPROVED)

	fun reject(): AdminCompanyImageVerification = copy(status = CompanyImageVerificationStatus.REJECTED)
}
```

- [ ] **Step 2: in-port(UseCase) 작성**

`command/application/port/in/ReviewCompanyImageVerificationUseCase.kt`:

```kotlin
package com.org.oneulsogae.admin.companyverification.command.application.port.`in`

/** 어드민 회사 이미지 인증 심사(승인/반려) 유스케이스. */
interface ReviewCompanyImageVerificationUseCase {

	/** 인증을 승인(APPROVED)하고 해당 유저의 회사명을 [companyName]으로 확정한다. */
	fun approve(id: Long, companyName: String)

	/** 인증을 반려(REJECTED)한다. */
	fun reject(id: Long)
}
```

- [ ] **Step 3: out-port 3개 작성**

`command/application/port/out/GetCompanyImageVerificationPort.kt`:

```kotlin
package com.org.oneulsogae.admin.companyverification.command.application.port.out

import com.org.oneulsogae.admin.companyverification.command.domain.AdminCompanyImageVerification

/** 심사 대상 인증을 로드하는 out-port. */
fun interface GetCompanyImageVerificationPort {

	/** [id]로 인증을 조회한다. 없거나 soft-delete면 null. */
	fun findById(id: Long): AdminCompanyImageVerification?
}
```

`command/application/port/out/SaveCompanyImageVerificationPort.kt`:

```kotlin
package com.org.oneulsogae.admin.companyverification.command.application.port.out

import com.org.oneulsogae.admin.companyverification.command.domain.AdminCompanyImageVerification

/** 인증 상태 변경을 저장하는 out-port. (status만 반영하고 다른 필드는 보존) */
fun interface SaveCompanyImageVerificationPort {

	fun save(verification: AdminCompanyImageVerification): AdminCompanyImageVerification
}
```

`command/application/port/out/UpdateUserCompanyNamePort.kt`:

```kotlin
package com.org.oneulsogae.admin.companyverification.command.application.port.out

/** 유저의 회사명을 갱신하는 out-port. (승인 시 어드민이 기입한 회사명을 프로필에 확정한다) */
fun interface UpdateUserCompanyNamePort {

	fun updateCompanyName(userId: Long, companyName: String)
}
```

- [ ] **Step 4: 서비스 작성**

`command/application/ReviewCompanyImageVerificationService.kt`:

```kotlin
package com.org.oneulsogae.admin.companyverification.command.application

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.companyverification.command.application.port.`in`.ReviewCompanyImageVerificationUseCase
import com.org.oneulsogae.admin.companyverification.command.application.port.out.GetCompanyImageVerificationPort
import com.org.oneulsogae.admin.companyverification.command.application.port.out.SaveCompanyImageVerificationPort
import com.org.oneulsogae.admin.companyverification.command.application.port.out.UpdateUserCompanyNamePort
import com.org.oneulsogae.admin.companyverification.command.domain.AdminCompanyImageVerification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [ReviewCompanyImageVerificationUseCase] 구현. 어드민이 직장 서류 인증을 승인/반려한다.
 * 승인: 인증 상태를 APPROVED로 바꾸고 어드민이 기입한 회사명을 유저 프로필에 확정한다.
 * 반려: 인증 상태만 REJECTED로 바꾼다.
 * (가입 상태 전환·코인·추천 등 부가 효과는 범위 밖)
 */
@Service
@Transactional
class ReviewCompanyImageVerificationService(
	private val getCompanyImageVerificationPort: GetCompanyImageVerificationPort,
	private val saveCompanyImageVerificationPort: SaveCompanyImageVerificationPort,
	private val updateUserCompanyNamePort: UpdateUserCompanyNamePort,
) : ReviewCompanyImageVerificationUseCase {

	override fun approve(id: Long, companyName: String) {
		val verification: AdminCompanyImageVerification = getCompanyImageVerificationPort.findById(id)
			?: throw AdminException(
				AdminErrorCode.COMPANY_IMAGE_VERIFICATION_NOT_FOUND,
				"직장 인증을 찾을 수 없습니다: $id",
			)
		saveCompanyImageVerificationPort.save(verification.approve())
		updateUserCompanyNamePort.updateCompanyName(verification.userId, companyName)
	}

	override fun reject(id: Long) {
		val verification: AdminCompanyImageVerification = getCompanyImageVerificationPort.findById(id)
			?: throw AdminException(
				AdminErrorCode.COMPANY_IMAGE_VERIFICATION_NOT_FOUND,
				"직장 인증을 찾을 수 없습니다: $id",
			)
		saveCompanyImageVerificationPort.save(verification.reject())
	}
}
```

- [ ] **Step 5: 컴파일 확인 + core 비의존 확인**

Run: `./gradlew :oneulsogae-admin:compileKotlin -q`
Expected: 성공(exit 0). (infra/api 미구현이라 전체 빌드는 Task 3 후 성공)

Run: `grep -rn "com.org.oneulsogae.core" oneulsogae-admin/src --include="*.kt" | wc -l | tr -d ' '`
Expected: `0`

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-admin/src/main/kotlin/com/org/oneulsogae/admin/companyverification/command
git commit -m "feat(admin): 회사 이미지 인증 승인/반려 command 슬라이스 추가

AdminCompanyImageVerification 도메인(approve/reject)·ReviewCompanyImageVerificationUseCase·
서비스·out-port(Get/Save 상태·UpdateUserCompanyName). 없는 id는 COMPANY-IMAGE-001.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: oneulsogae-infra — 어댑터 확장 (admin command out-port 구현)

**Files:**
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/adapter/CompanyImageVerificationRepositoryAdapter.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/adapter/UserDetailCoreAdapter.kt`

**Interfaces:**
- Consumes (Task 1): admin `GetCompanyImageVerificationPort`, `SaveCompanyImageVerificationPort`(alias), `UpdateUserCompanyNamePort`, `AdminCompanyImageVerification`.

- [ ] **Step 1: CompanyImageVerificationRepositoryAdapter에 admin 포트 추가**

`CompanyImageVerificationRepositoryAdapter.kt` 전체를 아래로 교체(기존 core save 유지 + admin Get/Save 추가):

```kotlin
package com.org.oneulsogae.infra.user.command.adapter

import com.org.oneulsogae.admin.companyverification.command.application.port.out.GetCompanyImageVerificationPort
import com.org.oneulsogae.admin.companyverification.command.application.port.out.SaveCompanyImageVerificationPort as SaveAdminCompanyImageVerificationPort
import com.org.oneulsogae.admin.companyverification.command.domain.AdminCompanyImageVerification
import com.org.oneulsogae.core.user.command.application.port.out.SaveCompanyImageVerificationPort
import com.org.oneulsogae.core.user.command.domain.CompanyImageVerification
import com.org.oneulsogae.infra.user.command.entity.CompanyImageVerificationEntity
import com.org.oneulsogae.infra.user.command.mapper.toDomain
import com.org.oneulsogae.infra.user.command.mapper.toEntity
import com.org.oneulsogae.infra.user.command.repository.CompanyImageVerificationJpaRepository
import org.springframework.stereotype.Component

/**
 * 직장 서류 이미지 인증 엔티티의 out-port 어댑터. (엔티티당 어댑터 하나)
 * core [SaveCompanyImageVerificationPort](제출 저장)와 admin 심사 포트([GetCompanyImageVerificationPort]·
 * [SaveAdminCompanyImageVerificationPort])를 함께 구현한다. (동명 Save 포트는 import alias로 구분)
 */
@Component
class CompanyImageVerificationRepositoryAdapter(
	private val companyImageVerificationJpaRepository: CompanyImageVerificationJpaRepository,
) : SaveCompanyImageVerificationPort, GetCompanyImageVerificationPort, SaveAdminCompanyImageVerificationPort {

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(verification: CompanyImageVerification): CompanyImageVerification =
		companyImageVerificationJpaRepository.save(verification.toEntity()).toDomain()

	// admin 심사: id로 인증을 조회한다. (@SQLRestriction으로 soft-delete 행 제외)
	override fun findById(id: Long): AdminCompanyImageVerification? =
		companyImageVerificationJpaRepository.findById(id)
			.map { entity: CompanyImageVerificationEntity -> entity.toAdminDomain() }
			.orElse(null)

	// admin 심사: 기존 행을 로드해 status만 바꿔 저장한다. (imageKey/userId 보존)
	override fun save(verification: AdminCompanyImageVerification): AdminCompanyImageVerification {
		val entity: CompanyImageVerificationEntity = companyImageVerificationJpaRepository.findById(verification.id)
			.orElseThrow { IllegalStateException("직장 인증을 찾을 수 없습니다: ${verification.id}") }
		entity.status = verification.status
		return companyImageVerificationJpaRepository.save(entity).toAdminDomain()
	}

	private fun CompanyImageVerificationEntity.toAdminDomain(): AdminCompanyImageVerification =
		AdminCompanyImageVerification(
			id = id ?: 0,
			userId = userId,
			status = status,
		)
}
```

- [ ] **Step 2: UserDetailCoreAdapter에 UpdateUserCompanyNamePort 추가**

`UserDetailCoreAdapter.kt`의 import 블록에 추가:

```kotlin
import com.org.oneulsogae.admin.companyverification.command.application.port.out.UpdateUserCompanyNamePort
import com.org.oneulsogae.infra.user.command.entity.UserDetailEntity
```

클래스 선언의 구현 인터페이스 목록에 `UpdateUserCompanyNamePort`를 추가한다:

```kotlin
) : GetUserDetailPort, SaveUserDetailPort, AnonymizeUserDetailPort, UpdateUserCompanyNamePort {
```

`anonymize(...)` 메서드 뒤(클래스 닫는 중괄호 앞)에 메서드 추가:

```kotlin
	// admin 심사 승인: 어드민이 기입한 회사명을 프로필에 확정한다. (정상 유저에겐 user_details 행이 항상 존재)
	override fun updateCompanyName(userId: Long, companyName: String) {
		val entity: UserDetailEntity = userDetailJpaRepository.findByUserId(userId)
			?: throw IllegalStateException("사용자 프로필을 찾을 수 없습니다: $userId")
		entity.companyName = companyName
		userDetailJpaRepository.save(entity)
	}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :oneulsogae-infra:compileKotlin -q`
Expected: 성공(exit 0).

- [ ] **Step 4: 커밋**

```bash
git add oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/adapter/CompanyImageVerificationRepositoryAdapter.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/adapter/UserDetailCoreAdapter.kt
git commit -m "feat(admin): 회사 이미지 인증 심사 out-port를 infra 어댑터에 구현

CompanyImageVerificationRepositoryAdapter에 admin Get/Save(상태) 포트(alias)를,
UserDetailCoreAdapter에 UpdateUserCompanyNamePort를 추가 구현한다.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: oneulsogae-api — 요청 DTO + 컨트롤러 approve/reject

**Files:**
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/request/AdminApproveCompanyVerificationRequest.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/AdminCompanyVerificationController.kt`

**Interfaces:**
- Consumes (Task 1): `ReviewCompanyImageVerificationUseCase.approve(id, companyName)`, `reject(id)`.
- Produces: `POST /admin/v1/company-image-verifications/{id}/approve`, `.../{id}/reject`.

- [ ] **Step 1: 승인 요청 DTO 작성**

`request/AdminApproveCompanyVerificationRequest.kt`:

```kotlin
package com.org.oneulsogae.api.admin.request

import jakarta.validation.constraints.NotBlank

/** 회사 이미지 인증 승인 요청. 어드민이 기입한 회사명을 유저 프로필에 확정한다. */
data class AdminApproveCompanyVerificationRequest(
	@field:NotBlank(message = "회사명은 필수입니다.")
	val companyName: String? = null,
)
```

- [ ] **Step 2: 컨트롤러에 UseCase 주입 + approve/reject 추가**

`AdminCompanyVerificationController.kt`의 import 블록에 추가:

```kotlin
import com.org.oneulsogae.admin.companyverification.command.application.port.`in`.ReviewCompanyImageVerificationUseCase
import com.org.oneulsogae.api.admin.request.AdminApproveCompanyVerificationRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
```

생성자에 UseCase를 추가한다(기존 파라미터 뒤에 콤마로):

```kotlin
class AdminCompanyVerificationController(
	private val getAdminCompanyVerificationsUseCase: GetAdminCompanyVerificationsUseCase,
	private val reviewCompanyImageVerificationUseCase: ReviewCompanyImageVerificationUseCase,
) {
```

`verification(...)` 상세 메서드 뒤(클래스 닫는 중괄호 앞)에 메서드 추가:

```kotlin
	@Operation(
		summary = "회사 이미지 인증 승인",
		description = "인증을 승인(APPROVED)하고 어드민이 기입한 회사명을 유저 프로필에 확정한다. 없으면 404(COMPANY-IMAGE-001), 회사명이 비면 400.",
	)
	@PostMapping("/{id}/approve")
	fun approve(
		@PathVariable id: Long,
		@RequestBody @Valid request: AdminApproveCompanyVerificationRequest,
	): ApiResponse<Unit> {
		reviewCompanyImageVerificationUseCase.approve(id, request.companyName!!)
		return ApiResponse.success()
	}

	@Operation(
		summary = "회사 이미지 인증 반려",
		description = "인증을 반려(REJECTED)한다. 없으면 404(COMPANY-IMAGE-001).",
	)
	@PostMapping("/{id}/reject")
	fun reject(
		@PathVariable id: Long,
	): ApiResponse<Unit> {
		reviewCompanyImageVerificationUseCase.reject(id)
		return ApiResponse.success()
	}
```

- [ ] **Step 3: 전체 컴파일 확인**

Run: `./gradlew :oneulsogae-api:compileKotlin -q`
Expected: 성공(exit 0).

- [ ] **Step 4: 커밋**

```bash
git add oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/request/AdminApproveCompanyVerificationRequest.kt \
        oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/admin/AdminCompanyVerificationController.kt
git commit -m "feat(admin): 회사 이미지 인증 승인/반려 컨트롤러·요청 DTO 추가

POST /admin/v1/company-image-verifications/{id}/approve(companyName)·/reject. 승인 요청 @NotBlank.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: E2E 테스트

**Files:**
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/admin/AdminCompanyVerificationReviewE2ETest.kt`

**Interfaces:**
- Consumes: `POST /admin/v1/company-image-verifications/{id}/approve|reject`, 픽스처, `IntegrationUtil`, `adminAccessTokenFor(id)`, E2E DSL(`post`/`expect`).

- [ ] **Step 1: E2E 테스트 작성**

`AdminCompanyVerificationReviewE2ETest.kt`:

```kotlin
package com.org.oneulsogae.api.admin

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.user.CompanyImageVerificationStatus
import com.org.oneulsogae.infra.fixture.CompanyImageVerificationEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.user.command.entity.CompanyImageVerificationEntity
import com.org.oneulsogae.infra.user.command.entity.QCompanyImageVerificationEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import com.org.oneulsogae.infra.user.command.entity.UserDetailEntity
import io.kotest.matchers.shouldBe

/**
 * `POST /admin/v1/company-image-verifications/{id}/approve|reject` E2E 테스트.
 * 승인: 인증 status를 APPROVED로 바꾸고 유저 user_details.companyName을 기입값으로 확정.
 * 반려: status를 REJECTED로. 없는 id 404(COMPANY-IMAGE-001), 공백 회사명 400.
 */
class AdminCompanyVerificationReviewE2ETest : AbstractIntegrationSupport({

	fun verificationById(id: Long): CompanyImageVerificationEntity {
		val v: QCompanyImageVerificationEntity = QCompanyImageVerificationEntity.companyImageVerificationEntity
		return IntegrationUtil.getQuery().selectFrom(v).where(v.id.eq(id)).fetchOne()!!
	}

	fun detailByUserId(userId: Long): UserDetailEntity {
		val d: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		return IntegrationUtil.getQuery().selectFrom(d).where(d.userId.eq(userId)).fetchOne()!!
	}

	describe("POST /admin/v1/company-image-verifications/{id}/approve") {

		it("승인하면 status=APPROVED로 바꾸고 유저 회사명을 확정한다 (200)") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "civ-approve")).id!!
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, nickname = "인증유저", companyName = null))
			val id: Long = IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(
					userId = userId,
					imageKey = "approve-key",
					status = CompanyImageVerificationStatus.PENDING,
				),
			).id!!

			post("/admin/v1/company-image-verifications/$id/approve") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"companyName":"오늘의 소개"}""")
			} expect {
				status(200)
				body("success", true)
			}

			verificationById(id).status shouldBe CompanyImageVerificationStatus.APPROVED
			detailByUserId(userId).companyName shouldBe "오늘의 소개"
		}

		it("공백 회사명이면 400이다") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "civ-blank")).id!!
			val id: Long = IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(userId = userId, imageKey = "blank-key"),
			).id!!

			post("/admin/v1/company-image-verifications/$id/approve") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"companyName":""}""")
			} expect {
				status(400)
				body("success", false)
			}
		}

		it("없는 id면 404다 (COMPANY-IMAGE-001)") {
			post("/admin/v1/company-image-verifications/999999/approve") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"companyName":"오늘의 소개"}""")
			} expect {
				status(404)
				body("error.code", "COMPANY-IMAGE-001")
			}
		}
	}

	describe("POST /admin/v1/company-image-verifications/{id}/reject") {

		it("반려하면 status=REJECTED로 바꾼다 (200)") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "civ-reject")).id!!
			val id: Long = IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(
					userId = userId,
					imageKey = "reject-key",
					status = CompanyImageVerificationStatus.PENDING,
				),
			).id!!

			post("/admin/v1/company-image-verifications/$id/reject") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("success", true)
			}

			verificationById(id).status shouldBe CompanyImageVerificationStatus.REJECTED
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QCompanyImageVerificationEntity.companyImageVerificationEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
```

- [ ] **Step 2: E2E 실행**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.admin.AdminCompanyVerificationReviewE2ETest" -q`
Expected: PASS (4개 케이스). Task 1~3 구현이 완료됐으므로 통과해야 한다. 실패 시:
- 테스트 코드 문제(픽스처·단언·정리)면 테스트를 고친다.
- 프로덕션 코드 버그면 최소 수정하되 보고서에 명시. **404가 500이면** AdminException 핸들러 우선순위(AdminExceptionHandler `@Order(HIGHEST_PRECEDENCE)`)·에러코드 매핑을, **승인 후 companyName 미반영이면** UserDetailCoreAdapter 갱신·트랜잭션 커밋을 점검. 확신 안 서면 BLOCKED.

- [ ] **Step 3: 전체 빌드·테스트 확인**

Run: `./gradlew build -q`
Expected: 성공(exit 0). (조회 E2E·기존 테스트 회귀 없음)

- [ ] **Step 4: 커밋**

```bash
git add oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/admin/AdminCompanyVerificationReviewE2ETest.kt
git commit -m "test(admin): 회사 이미지 인증 승인/반려 E2E 추가

승인(status=APPROVED·companyName 확정)·반려(status=REJECTED)·404(COMPANY-IMAGE-001)·공백 회사명 400을 검증한다.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review 결과

- **Spec coverage**: 승인(Task 1 Service·2 어댑터·3 컨트롤러·4 테스트)·반려(동일)·회사명 확정(Task 1 UpdateUserCompanyNamePort·2 UserDetailCoreAdapter·4 테스트)·404(Task 1 AdminException·4 테스트)·400 공백(Task 3 @NotBlank·4 테스트)·엔티티당 어댑터+alias(Task 2)·admin core 비의존(Task 1 Step 5) 모두 커버.
- **Placeholder scan**: 모든 코드 스텝에 실제 코드, TODO/TBD 없음.
- **Type consistency**: `approve(id: Long, companyName: String)`/`reject(id: Long)` / `findById(id): AdminCompanyImageVerification?` / `save(AdminCompanyImageVerification): AdminCompanyImageVerification` / `updateCompanyName(userId: Long, companyName: String)` / `AdminCompanyImageVerification(id, userId, status)` 명칭이 Task 1~4에서 일치. infra는 core/admin 동명 `SaveCompanyImageVerificationPort`를 alias(`SaveAdminCompanyImageVerificationPort`)로 구분해 두 `save` 오버로드를 구현. 컨트롤러는 `request.companyName!!`로 검증 후 비널 전달.
