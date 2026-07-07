# 회사 이미지 인증: 제출 희망 회사명 + 거절사유 저장 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `company_image_verifications`에 유저 제출 희망 회사명(`company_name`)과 어드민 거절사유(`rejection_reason`) 컬럼을 추가하고, 유저 제출·어드민 반려·어드민 상세 조회 흐름에 배선한다.

**Architecture:** 엔티티에 nullable 컬럼 2개를 추가하고(운영은 마이그레이션 SQL, 테스트는 ddl-auto create-drop), core 제출 vertical(도메인·command·서비스·컨트롤러)에 companyName을, meeple-admin 반려 흐름에 rejectionReason을 더한다. 어드민 상세 read model에 두 값을 노출한다.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4 / Spring Data JPA / QueryDSL / Kotest(유닛·E2E) / MySQL.

## Global Constraints

- **컬럼 2개 모두 NULL 허용**: `company_name VARCHAR(100) NULL`, `rejection_reason VARCHAR(500) NULL`. (기존 행 호환)
- **제출 companyName은 도메인에서 필수 검증**: 공백/50자 초과 → `UserErrorCode.INVALID_COMPANY_NAME`("USER-023", BAD_REQUEST). 상한 50자.
- **거절사유는 선택(nullable)**, reject body는 `required = false`. `@field:Size(max = 500)`.
- **approve는 직전 동작 유지 + rejectionReason 초기화(null)**. reject는 상태 REJECTED + 사유 저장.
- **`meeple-admin`은 `meeple-core`를 의존하지 않는다** (admin 소스 `com.org.meeple.core.*` 0건).
- **타입 명시**. CQRS(명령 서비스 `@Transactional`). 커밋 형식 `<type>(<domain>): <설명>`.
- 커밋 트레일러: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

## Task 1: Part A — 유저 제출 희망 회사명 (도메인·엔티티·매퍼·command·서비스·컨트롤러·에러코드·마이그레이션·유닛·픽스처·제출 E2E)

**Files:**
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/user/UserErrorCode.kt`
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/user/command/domain/CompanyImageVerification.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/user/command/entity/CompanyImageVerificationEntity.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/user/command/mapper/CompanyImageVerificationMapper.kt`
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/user/command/application/port/in/command/SubmitCompanyImageVerificationCommand.kt`
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/user/command/application/SubmitCompanyImageVerificationService.kt`
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/api/user/UserCompanyImageVerificationController.kt`
- Modify: `meeple-infra/src/testFixtures/kotlin/com/org/meeple/infra/fixture/CompanyImageVerificationEntityFixture.kt`
- Modify: `meeple-api/src/test/kotlin/com/org/meeple/domain/user/CompanyImageVerificationTest.kt`
- Modify: `meeple-api/src/test/kotlin/com/org/meeple/api/user/SubmitCompanyImageVerificationE2ETest.kt`
- Create: `docs/migration/company_image_verifications_company_name_rejection_reason.sql`

**Interfaces:**
- Produces:
  - `CompanyImageVerification(id, userId, imageKey, status, companyName: String?, rejectionReason: String?)` + `create(userId: Long, imageKey: String, companyName: String)` + `validateCompanyName(companyName: String)` + `MAX_COMPANY_NAME_LENGTH = 50`.
  - `CompanyImageVerificationEntity`에 `var companyName: String?`·`var rejectionReason: String?`.
  - `SubmitCompanyImageVerificationCommand(content, contentType, size, companyName: String)`.
  - `UserErrorCode.INVALID_COMPANY_NAME`.
  - `CompanyImageVerificationEntityFixture.create(..., companyName, rejectionReason)`.

- [ ] **Step 1: UserErrorCode에 INVALID_COMPANY_NAME 추가**

`UserErrorCode.kt`의 `IMAGE_TOO_LARGE(...)` 줄 뒤에 추가(기존 형식대로 콤마 유지):

```kotlin
	IMAGE_TOO_LARGE("USER-022", "파일이 너무 큽니다. 최대 10MB까지 업로드할 수 있습니다.", HttpStatus.PAYLOAD_TOO_LARGE),
	INVALID_COMPANY_NAME("USER-023", "회사명을 입력해 주세요. (최대 50자)", HttpStatus.BAD_REQUEST),
```

- [ ] **Step 2: 도메인 유닛 테스트 작성/갱신 (RED)**

`CompanyImageVerificationTest.kt`의 `describe("create")` 블록을 아래로 교체한다(기존 create 호출에 companyName 추가 + 신규 케이스):

```kotlin
	describe("create") {
		it("제출은 심사 대기(PENDING)로 시작하고 희망 회사명을 담는다") {
			val verification: CompanyImageVerification =
				CompanyImageVerification.create(userId = 1L, imageKey = "k/1/a.jpg", companyName = "미플")

			verification.userId shouldBe 1L
			verification.imageKey shouldBe "k/1/a.jpg"
			verification.companyName shouldBe "미플"
			verification.status shouldBe CompanyImageVerificationStatus.PENDING
			verification.rejectionReason shouldBe null
		}

		it("imageKey가 비면 생성할 수 없다") {
			shouldThrow<IllegalArgumentException> {
				CompanyImageVerification.create(userId = 1L, imageKey = " ", companyName = "미플")
			}
		}

		it("회사명이 비면 INVALID_COMPANY_NAME을 던진다") {
			val exception: BusinessException = shouldThrow<BusinessException> {
				CompanyImageVerification.create(userId = 1L, imageKey = "k/1/a.jpg", companyName = " ")
			}
			exception.errorCode shouldBe UserErrorCode.INVALID_COMPANY_NAME
		}

		it("회사명이 50자를 넘으면 INVALID_COMPANY_NAME을 던진다") {
			val exception: BusinessException = shouldThrow<BusinessException> {
				CompanyImageVerification.create(userId = 1L, imageKey = "k/1/a.jpg", companyName = "가".repeat(51))
			}
			exception.errorCode shouldBe UserErrorCode.INVALID_COMPANY_NAME
		}
	}
```

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.domain.user.CompanyImageVerificationTest" -q`
Expected: FAIL (컴파일 에러 — `create`에 companyName 인자 없음/`companyName` 프로퍼티 없음).

- [ ] **Step 3: 도메인에 companyName·rejectionReason·검증 추가 (GREEN)**

`CompanyImageVerification.kt`의 data class 필드와 companion을 아래로 바꾼다.

data class 헤더(필드 순서 — 새 필드는 status 뒤에 둔다):

```kotlin
data class CompanyImageVerification(
	val id: Long = 0,
	val userId: Long,
	val imageKey: String,
	val status: CompanyImageVerificationStatus = CompanyImageVerificationStatus.PENDING,
	/** 유저가 제출 시 기입한 희망 회사명. (어드민 심사 근거) */
	val companyName: String? = null,
	/** 어드민 반려 사유. 반려 시에만 채워진다. */
	val rejectionReason: String? = null,
) {
```

companion object의 `create`를 아래로 바꾸고 `MAX_COMPANY_NAME_LENGTH`·`validateCompanyName`을 추가한다(기존 `ALLOWED_CONTENT_TYPES`·`MAX_FILE_SIZE_BYTES` 아래):

```kotlin
		/** 제출 희망 회사명의 최대 길이. (회사명 직접입력·어드민 승인 DTO와 동일 상한) */
		const val MAX_COMPANY_NAME_LENGTH: Int = 50

		/** 신규 제출(심사 대기)을 생성한다. 희망 회사명([companyName])을 검증해 담는다. */
		fun create(userId: Long, imageKey: String, companyName: String): CompanyImageVerification {
			validateCompanyName(companyName)
			return CompanyImageVerification(userId = userId, imageKey = imageKey, companyName = companyName)
		}

		/** 제출 희망 회사명 검증. 공백이거나 [MAX_COMPANY_NAME_LENGTH]자를 넘으면 [UserErrorCode.INVALID_COMPANY_NAME]. */
		fun validateCompanyName(companyName: String) {
			if (companyName.isBlank() || companyName.length > MAX_COMPANY_NAME_LENGTH) {
				throw BusinessException(UserErrorCode.INVALID_COMPANY_NAME)
			}
		}
```

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.domain.user.CompanyImageVerificationTest" -q`
Expected: PASS (도메인 컴파일·유닛 통과. infra/api는 아직 미갱신이라 전체 빌드는 뒤 스텝 후).

- [ ] **Step 4: 엔티티에 컬럼 추가**

`CompanyImageVerificationEntity.kt`의 `status` 프로퍼티 뒤(닫는 `)` 앞)에 추가:

```kotlin
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	var status: CompanyImageVerificationStatus = CompanyImageVerificationStatus.PENDING,

	@Column(name = "company_name", length = 100)
	var companyName: String? = null,

	@Column(name = "rejection_reason", length = 500)
	var rejectionReason: String? = null,
) : BaseEntity()
```

- [ ] **Step 5: 매퍼에 두 필드 반영**

`CompanyImageVerificationMapper.kt`를 아래로 교체:

```kotlin
package com.org.meeple.infra.user.command.mapper

import com.org.meeple.core.user.command.domain.CompanyImageVerification
import com.org.meeple.infra.user.command.entity.CompanyImageVerificationEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun CompanyImageVerificationEntity.toDomain(): CompanyImageVerification =
	CompanyImageVerification(
		id = id ?: 0,
		userId = userId,
		imageKey = imageKey,
		status = status,
		companyName = companyName,
		rejectionReason = rejectionReason,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun CompanyImageVerification.toEntity(): CompanyImageVerificationEntity =
	CompanyImageVerificationEntity(
		userId = userId,
		imageKey = imageKey,
		status = status,
		companyName = companyName,
		rejectionReason = rejectionReason,
	).also { if (id != 0L) it.id = id }
```

- [ ] **Step 6: command·서비스·컨트롤러에 companyName 배선**

`SubmitCompanyImageVerificationCommand.kt`를 아래로 교체:

```kotlin
package com.org.meeple.core.user.command.application.port.`in`.command

/**
 * 직장 서류 이미지 인증 제출 명령. 컨트롤러(api)가 MultipartFile·폼 필드에서 값을 뽑아 넘긴다.
 * (core는 웹 타입에 의존하지 않도록 원시 바이트·메타만 받는다)
 */
data class SubmitCompanyImageVerificationCommand(
	val content: ByteArray,
	val contentType: String?,
	val size: Long,
	val companyName: String,
)
```

`SubmitCompanyImageVerificationService.kt`의 저장 라인을 바꾼다:

```kotlin
		return saveCompanyImageVerificationPort.save(
			CompanyImageVerification.create(userId = user.id, imageKey = key, companyName = command.companyName),
		)
```

`UserCompanyImageVerificationController.kt`의 `submitCompanyImageVerification` 파라미터·command 생성을 바꾼다(파라미터에 companyName 추가):

```kotlin
	fun submitCompanyImageVerification(
		@LoginUser user: AuthUser,
		@RequestParam("image") image: MultipartFile,
		@RequestParam("companyName") companyName: String,
	): ApiResponse<CompanyImageVerificationResponse> {
		val command = SubmitCompanyImageVerificationCommand(
			content = image.bytes,
			contentType = image.contentType,
			size = image.size,
			companyName = companyName,
		)
		return ApiResponse.success(
			CompanyImageVerificationResponse.of(submitCompanyImageVerificationUseCase.submit(user.id, command)),
		)
	}
```

- [ ] **Step 7: 엔티티 픽스처에 파라미터 추가**

`CompanyImageVerificationEntityFixture.kt`의 `create`를 아래로 교체:

```kotlin
	fun create(
		userId: Long = 1L,
		imageKey: String = "company-image-verifications/1/test-object.jpg",
		status: CompanyImageVerificationStatus = CompanyImageVerificationStatus.PENDING,
		companyName: String? = "테스트회사",
		rejectionReason: String? = null,
	): CompanyImageVerificationEntity =
		CompanyImageVerificationEntity(
			userId = userId,
			imageKey = imageKey,
			status = status,
			companyName = companyName,
			rejectionReason = rejectionReason,
		)
```

- [ ] **Step 8: 제출 E2E 갱신 (companyName 파트 추가 + 검증 케이스)**

`SubmitCompanyImageVerificationE2ETest.kt`에서 **기존 모든** `RestAssured.given()...` 요청의 `.multiPart("image", ...)` 뒤에 `.multiPart("companyName", "미플")`를 추가한다. 정상 업로드 케이스에는 저장 검증 `saved.companyName shouldBe "미플"`을 추가한다(`saved`는 이미 로드하는 `latestVerificationOf(userId)!!`). 그리고 아래 검증 케이스를 `describe` 블록 안에 추가한다:

```kotlin
		context("회사명 없이 업로드하면") {
			it("400을 반환한다") {
				val userId: Long = persistUser("company-image-noname")

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.multiPart("image", "resume.jpg", "fake-image-bytes".toByteArray(), "image/jpeg")
					.post("/users/v1/company-image/verifications")
					.then()
					.statusCode(400)
			}
		}
```

(companyName 누락은 `@RequestParam` 필수 위반으로 400. 공백/50자 초과는 도메인 검증(INVALID_COMPANY_NAME)이 Step 2 유닛에서 커버됨.)

- [ ] **Step 9: 마이그레이션 SQL 작성**

`docs/migration/company_image_verifications_company_name_rejection_reason.sql`:

```sql
-- company_image_verifications: 제출 희망 회사명·반려 사유 컬럼 추가.
-- company_name: 유저가 제출 시 기입한 희망 회사명(어드민 심사 근거). 기존 행은 NULL.
-- rejection_reason: 어드민 반려 사유. 반려 시에만 채워진다.
ALTER TABLE company_image_verifications ADD COLUMN company_name VARCHAR(100) NULL;
ALTER TABLE company_image_verifications ADD COLUMN rejection_reason VARCHAR(500) NULL;
```

- [ ] **Step 10: 검증 (유닛 + 제출 E2E + 컴파일)**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.domain.user.CompanyImageVerificationTest" --tests "com.org.meeple.api.user.SubmitCompanyImageVerificationE2ETest" -q`
Expected: PASS (유닛 + 제출 E2E). (admin 쪽은 후속 태스크라 전체 빌드는 Task 3 후 성공)

- [ ] **Step 11: 커밋**

```bash
git add meeple-core/src/main/kotlin/com/org/meeple/core/user/UserErrorCode.kt \
        meeple-core/src/main/kotlin/com/org/meeple/core/user/command/domain/CompanyImageVerification.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/user/command/entity/CompanyImageVerificationEntity.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/user/command/mapper/CompanyImageVerificationMapper.kt \
        "meeple-core/src/main/kotlin/com/org/meeple/core/user/command/application/port/in/command/SubmitCompanyImageVerificationCommand.kt" \
        meeple-core/src/main/kotlin/com/org/meeple/core/user/command/application/SubmitCompanyImageVerificationService.kt \
        meeple-api/src/main/kotlin/com/org/meeple/api/user/UserCompanyImageVerificationController.kt \
        meeple-infra/src/testFixtures/kotlin/com/org/meeple/infra/fixture/CompanyImageVerificationEntityFixture.kt \
        meeple-api/src/test/kotlin/com/org/meeple/domain/user/CompanyImageVerificationTest.kt \
        meeple-api/src/test/kotlin/com/org/meeple/api/user/SubmitCompanyImageVerificationE2ETest.kt \
        docs/migration/company_image_verifications_company_name_rejection_reason.sql
git commit -m "feat(user): 회사 이미지 인증 제출에 희망 회사명 저장

멀티파트 companyName(필수·최대 50자, INVALID_COMPANY_NAME) → 도메인·엔티티·매퍼·command·서비스 배선.
rejection_reason 컬럼도 함께 추가(어드민 반려에서 사용). 운영 마이그레이션 SQL 포함.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Part B — 거절사유 (admin reject 배선)

**Files:**
- Modify: `meeple-admin/src/main/kotlin/com/org/meeple/admin/companyverification/command/domain/AdminCompanyImageVerification.kt`
- Modify: `meeple-admin/src/main/kotlin/com/org/meeple/admin/companyverification/command/application/port/in/ReviewCompanyImageVerificationUseCase.kt`
- Modify: `meeple-admin/src/main/kotlin/com/org/meeple/admin/companyverification/command/application/ReviewCompanyImageVerificationService.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/user/command/adapter/CompanyImageVerificationRepositoryAdapter.kt`
- Create: `meeple-api/src/main/kotlin/com/org/meeple/api/admin/request/AdminRejectCompanyVerificationRequest.kt`
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/api/admin/AdminCompanyVerificationController.kt`

**Interfaces:**
- Consumes (Task 1): `CompanyImageVerificationEntity.rejectionReason`(var).
- Produces: `AdminCompanyImageVerification(id, userId, status, rejectionReason: String?)` + `approve()`(사유 null) + `reject(reason: String?)`; `ReviewCompanyImageVerificationUseCase.reject(id: Long, reason: String?)`.

- [ ] **Step 1: 어드민 도메인에 rejectionReason·전이 반영**

`AdminCompanyImageVerification.kt`를 아래로 교체:

```kotlin
package com.org.meeple.admin.companyverification.command.domain

import com.org.meeple.common.user.CompanyImageVerificationStatus

/**
 * 어드민 심사용 회사 이미지 인증 도메인 모델(최소). 상태 전이(승인/반려)와 반려 사유를 캡슐화한다.
 * (admin은 core에 의존하지 않으므로 core CompanyImageVerification을 쓰지 않고 심사에 필요한 최소 필드만 둔다)
 */
data class AdminCompanyImageVerification(
	val id: Long,
	val userId: Long,
	val status: CompanyImageVerificationStatus,
	val rejectionReason: String? = null,
) {
	/** 승인. 이전에 반려로 남았을 수 있는 사유를 초기화한다. */
	fun approve(): AdminCompanyImageVerification =
		copy(status = CompanyImageVerificationStatus.APPROVED, rejectionReason = null)

	/** 반려. 사유([reason], 선택)를 함께 남긴다. */
	fun reject(reason: String?): AdminCompanyImageVerification =
		copy(status = CompanyImageVerificationStatus.REJECTED, rejectionReason = reason)
}
```

- [ ] **Step 2: UseCase·서비스 reject 시그니처에 reason 추가**

`ReviewCompanyImageVerificationUseCase.kt`의 `reject`를 바꾼다:

```kotlin
	/** 인증을 반려(REJECTED)하고 사유([reason], 선택)를 남긴다. */
	fun reject(id: Long, reason: String?)
```

`ReviewCompanyImageVerificationService.kt`의 `reject`를 바꾼다:

```kotlin
	override fun reject(id: Long, reason: String?) {
		val verification: AdminCompanyImageVerification = getCompanyImageVerificationPort.findById(id)
			?: throw AdminException(
				AdminErrorCode.COMPANY_IMAGE_VERIFICATION_NOT_FOUND,
				"직장 인증을 찾을 수 없습니다: $id",
			)
		saveCompanyImageVerificationPort.save(verification.reject(reason))
	}
```

- [ ] **Step 3: infra 어댑터가 rejectionReason 반영**

`CompanyImageVerificationRepositoryAdapter.kt`의 admin `save`와 `toAdminDomain`을 바꾼다:

```kotlin
	// admin 심사: 기존 행을 로드해 status·rejectionReason을 반영해 저장한다. (imageKey/userId/companyName 보존)
	override fun save(verification: AdminCompanyImageVerification): AdminCompanyImageVerification {
		val entity: CompanyImageVerificationEntity = companyImageVerificationJpaRepository.findById(verification.id)
			.orElseThrow { IllegalStateException("직장 인증을 찾을 수 없습니다: ${verification.id}") }
		entity.status = verification.status
		entity.rejectionReason = verification.rejectionReason
		return companyImageVerificationJpaRepository.save(entity).toAdminDomain()
	}

	private fun CompanyImageVerificationEntity.toAdminDomain(): AdminCompanyImageVerification =
		AdminCompanyImageVerification(
			id = id ?: 0,
			userId = userId,
			status = status,
			rejectionReason = rejectionReason,
		)
```

- [ ] **Step 4: 반려 요청 DTO + 컨트롤러 reject body**

`request/AdminRejectCompanyVerificationRequest.kt`:

```kotlin
package com.org.meeple.api.admin.request

import jakarta.validation.constraints.Size

/** 회사 이미지 인증 반려 요청. 반려 사유(선택)를 저장한다. */
data class AdminRejectCompanyVerificationRequest(
	@field:Size(max = 500, message = "반려 사유는 500자 이하여야 합니다.")
	val reason: String? = null,
)
```

`AdminCompanyVerificationController.kt`의 import에 추가:

```kotlin
import com.org.meeple.api.admin.request.AdminRejectCompanyVerificationRequest
```

`reject` 메서드를 아래로 교체(body 수용):

```kotlin
	@Operation(
		summary = "회사 이미지 인증 반려",
		description = "인증을 반려(REJECTED)하고 사유(선택)를 저장한다. 없으면 404(COMPANY-IMAGE-001).",
	)
	@PostMapping("/{id}/reject")
	fun reject(
		@PathVariable id: Long,
		@RequestBody(required = false) @Valid request: AdminRejectCompanyVerificationRequest?,
	): ApiResponse<Unit> {
		reviewCompanyImageVerificationUseCase.reject(id, request?.reason)
		return ApiResponse.success()
	}
```

- [ ] **Step 5: 컴파일 확인 + core 비의존 확인**

Run: `./gradlew :meeple-api:compileKotlin -q`
Expected: 성공(exit 0).

Run: `grep -rn "com.org.meeple.core" meeple-admin/src --include="*.kt" | wc -l | tr -d ' '`
Expected: `0`

- [ ] **Step 6: 커밋**

```bash
git add meeple-admin/src/main/kotlin/com/org/meeple/admin/companyverification/command \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/user/command/adapter/CompanyImageVerificationRepositoryAdapter.kt \
        meeple-api/src/main/kotlin/com/org/meeple/api/admin/request/AdminRejectCompanyVerificationRequest.kt \
        meeple-api/src/main/kotlin/com/org/meeple/api/admin/AdminCompanyVerificationController.kt
git commit -m "feat(admin): 회사 이미지 인증 반려 사유 저장

reject(id, reason) — AdminCompanyImageVerification.reject(reason)로 상태·사유 전이, infra 어댑터가 저장.
approve는 사유 초기화. 반려 요청 DTO(@Size 500, 선택)·컨트롤러 body(required=false) 추가.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Part C — 어드민 상세 노출 (requestedCompanyName·rejectionReason)

**Files:**
- Modify: `meeple-admin/src/main/kotlin/com/org/meeple/admin/companyverification/query/dto/AdminCompanyVerificationDetailView.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/user/query/GetAdminCompanyVerificationDaoImpl.kt`
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/api/admin/response/AdminCompanyVerificationDetailResponse.kt`

**Interfaces:**
- Consumes (Task 1): `CompanyImageVerificationEntity.companyName`·`rejectionReason`.
- Produces: `AdminCompanyVerificationDetailView`에 `requestedCompanyName`·`rejectionReason` 추가(12-arg 보조 생성자).

- [ ] **Step 1: 상세 read model에 필드 추가**

`AdminCompanyVerificationDetailView.kt`를 아래로 교체:

```kotlin
package com.org.meeple.admin.companyverification.query.dto

import com.org.meeple.common.user.CompanyImageVerificationStatus
import java.time.LocalDateTime

/**
 * 어드민 회사 이미지 인증 상세 read model. 목록 필드 + 사용자가 주장한 직장 정보(companyName·companyEmail·job) +
 * 유저가 제출 시 기입한 희망 회사명([requestedCompanyName])·어드민 반려 사유([rejectionReason]).
 * dao는 [imageKey]까지 채우고 [imageUrl]은 null로 둔다. 서비스가 presign 결과로 [imageUrl]을 채운다.
 * (QueryDSL Projections.constructor가 imageUrl 없이 투영할 수 있도록 12-arg 보조 생성자를 둔다)
 */
data class AdminCompanyVerificationDetailView(
	val id: Long,
	val userId: Long,
	val nickname: String?,
	val email: String?,
	val status: CompanyImageVerificationStatus,
	val createdAt: LocalDateTime?,
	val imageKey: String,
	/** user_details 프로필 회사명. */
	val companyName: String?,
	val companyEmail: String?,
	val job: String?,
	/** 유저가 제출 시 기입한 희망 회사명. (company_image_verifications.company_name) */
	val requestedCompanyName: String?,
	/** 어드민 반려 사유. */
	val rejectionReason: String?,
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
		companyName: String?,
		companyEmail: String?,
		job: String?,
		requestedCompanyName: String?,
		rejectionReason: String?,
	) : this(id, userId, nickname, email, status, createdAt, imageKey, companyName, companyEmail, job, requestedCompanyName, rejectionReason, null)
}
```

- [ ] **Step 2: daoImpl findDetailById 투영에 두 컬럼 추가**

`GetAdminCompanyVerificationDaoImpl.kt`의 `findDetailById` `Projections.constructor` 인자에서 `detail.job` 뒤에 `verification.companyName`·`verification.rejectionReason`을 추가한다:

```kotlin
				Projections.constructor(
					AdminCompanyVerificationDetailView::class.java,
					verification.id,
					verification.userId,
					detail.nickname,
					user.email,
					verification.status,
					verification.createdAt,
					verification.imageKey,
					detail.companyName,
					detail.companyEmail,
					detail.job,
					verification.companyName,
					verification.rejectionReason,
				),
```

- [ ] **Step 3: 상세 응답에 필드 추가**

`AdminCompanyVerificationDetailResponse.kt`의 data class에 `requestedCompanyName`·`rejectionReason`을 추가하고 `of`에 매핑을 추가한다.

data class에 `job` 뒤·`imageUrl` 앞에 추가:

```kotlin
	val job: String?,
	val requestedCompanyName: String?,
	val rejectionReason: String?,
	val imageUrl: String?,
```

`of`의 `job = view.job,` 뒤·`imageUrl = view.imageUrl,` 앞에 추가:

```kotlin
				job = view.job,
				requestedCompanyName = view.requestedCompanyName,
				rejectionReason = view.rejectionReason,
				imageUrl = view.imageUrl,
```

- [ ] **Step 4: 전체 컴파일 확인**

Run: `./gradlew :meeple-api:compileKotlin -q`
Expected: 성공(exit 0).

- [ ] **Step 5: 커밋**

```bash
git add meeple-admin/src/main/kotlin/com/org/meeple/admin/companyverification/query/dto/AdminCompanyVerificationDetailView.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/user/query/GetAdminCompanyVerificationDaoImpl.kt \
        meeple-api/src/main/kotlin/com/org/meeple/api/admin/response/AdminCompanyVerificationDetailResponse.kt
git commit -m "feat(admin): 회사 이미지 인증 상세에 제출 희망 회사명·반려 사유 노출

AdminCompanyVerificationDetailView에 requestedCompanyName(verification.company_name)·rejectionReason 추가,
daoImpl 투영·상세 응답 매핑. 어드민이 유저 제출 회사명과 반려 사유를 상세에서 확인한다.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: 어드민 E2E 갱신 (반려 사유 저장·상세 노출)

**Files:**
- Modify: `meeple-api/src/test/kotlin/com/org/meeple/api/admin/AdminCompanyVerificationReviewE2ETest.kt`
- Modify: `meeple-api/src/test/kotlin/com/org/meeple/api/admin/AdminCompanyVerificationDetailE2ETest.kt`

**Interfaces:**
- Consumes: `POST .../{id}/reject`(body reason), 상세 `requestedCompanyName`·`rejectionReason`.

- [ ] **Step 1: 반려 E2E에 사유 저장 검증 추가**

`AdminCompanyVerificationReviewE2ETest.kt`의 반려 `describe` 블록 안 기존 반려 케이스 뒤에 케이스를 추가한다(사유 저장 검증). 기존 `verificationById` 헬퍼를 재사용한다:

```kotlin
		it("반려 시 사유를 저장한다 (200)") {
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "civ-reject-reason")).id!!
			val id: Long = IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(
					userId = userId,
					imageKey = "reject-reason-key",
					status = CompanyImageVerificationStatus.PENDING,
				),
			).id!!

			post("/admin/v1/company-image-verifications/$id/reject") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"reason":"서류가 불명확합니다"}""")
			} expect {
				status(200)
				body("success", true)
			}

			val rejected: CompanyImageVerificationEntity = verificationById(id)
			rejected.status shouldBe CompanyImageVerificationStatus.REJECTED
			rejected.rejectionReason shouldBe "서류가 불명확합니다"
		}
```

- [ ] **Step 2: 상세 E2E에 제출 회사명·반려 사유 노출 검증 추가**

`AdminCompanyVerificationDetailE2ETest.kt`의 상세 `describe` 블록에 케이스를 추가한다. 픽스처는 `companyName`(제출 희망), 반려 상태·사유를 담아 persist한다:

```kotlin
		it("제출 희망 회사명과 반려 사유를 노출한다") {
			val userId: Long = IntegrationUtil.persist(
				UserEntityFixture.create(providerId = "civ-detail-extra", email = "cde@test.com"),
			).id!!
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, nickname = "인증유저"))
			val id: Long = IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(
					userId = userId,
					imageKey = "extra-key",
					status = CompanyImageVerificationStatus.REJECTED,
					companyName = "지원회사",
					rejectionReason = "서류 재제출 필요",
				),
			).id!!

			get("/admin/v1/company-image-verifications/$id") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.requestedCompanyName", "지원회사")
				body("data.rejectionReason", "서류 재제출 필요")
			}
		}
```

(필요 import: `com.org.meeple.common.user.CompanyImageVerificationStatus`, `com.org.meeple.infra.fixture.UserDetailEntityFixture` 등 파일에 없으면 추가. `AdminCompanyVerificationReviewE2ETest`에는 `CompanyImageVerificationEntity` import가 이미 있음.)

- [ ] **Step 3: 어드민 E2E 실행**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.admin.AdminCompanyVerificationReviewE2ETest" --tests "com.org.meeple.api.admin.AdminCompanyVerificationDetailE2ETest" -q`
Expected: PASS. 실패 시 원인 진단(사유 매핑·상세 투영·픽스처 시그니처). 프로덕션 버그면 최소 수정 후 보고, 확신 안 서면 BLOCKED.

- [ ] **Step 4: 전체 빌드 확인**

Run: `./gradlew build -q`
Expected: 성공(exit 0). (제출 E2E·조회·승인/반려 등 기존 테스트 회귀 없음)

- [ ] **Step 5: 커밋**

```bash
git add meeple-api/src/test/kotlin/com/org/meeple/api/admin/AdminCompanyVerificationReviewE2ETest.kt \
        meeple-api/src/test/kotlin/com/org/meeple/api/admin/AdminCompanyVerificationDetailE2ETest.kt
git commit -m "test(admin): 반려 사유 저장·상세 노출 E2E 추가

reject(reason)가 rejection_reason을 저장하고, 상세가 requestedCompanyName·rejectionReason을 노출함을 검증한다.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review 결과

- **Spec coverage**: 스키마 2컬럼(Task 1 엔티티+마이그레이션)·제출 companyName(Task 1)·INVALID_COMPANY_NAME(Task 1)·거절사유 저장(Task 2)·approve 사유 초기화(Task 2 도메인)·상세 노출(Task 3)·검증(Task 1 유닛/제출E2E, Task 4 어드민E2E)·admin core 비의존(Task 2 Step 5) 모두 커버.
- **Placeholder scan**: 코드 스텝에 실제 코드. E2E 갱신 스텝은 기존 파일 편집이라 추가 케이스 전체 코드 + 구체 편집 지시 제공(구현자가 파일을 읽고 반영).
- **Type consistency**: `create(userId, imageKey, companyName)` / `SubmitCompanyImageVerificationCommand(...companyName)` / `AdminCompanyImageVerification(id, userId, status, rejectionReason)` + `reject(reason)` / `ReviewCompanyImageVerificationUseCase.reject(id, reason)` / `AdminCompanyVerificationDetailView`(13필드, 12-arg 보조 생성자) / daoImpl 투영 12인자 / 응답 requestedCompanyName·rejectionReason 명칭이 Task 1~4에서 일치. 엔티티 `companyName`/`rejectionReason`은 `var`.
