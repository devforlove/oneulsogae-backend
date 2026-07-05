# 어드민 회사 이미지 인증 상세조회 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 어드민이 직장 서류 인증 한 건을 `GET /admin/v1/company-image-verifications/{id}`로 상세 조회한다. 목록 필드에 사용자가 주장한 직장 정보(companyName·companyEmail·job)를 더하고, 서류 열람용 presigned URL을 포함한다.

**Architecture:** 기존 목록 조회 슬라이스(`admin/companyverification/query`)와 신고 상세(`GET /admin/v1/reports/{id}`)를 미러링한다. 기존 dao/UseCase/Service에 상세 메서드를 추가하고, presign은 기존 `CompanyVerificationImageUrlPort`를 재사용한다. 없는 id는 신규 `AdminErrorCode`로 404.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4 / Spring Data JPA / QueryDSL / Kotest E2E(Testcontainers).

## Global Constraints

- **`meeple-admin`은 `meeple-core`를 의존하지 않는다.** admin 소스에 `com.org.meeple.core.*` import 0건.
- **타입 명시**: 변수·반환 타입·람다 파라미터 타입 생략 금지.
- **CQRS**: 조회 전용. 기존 서비스는 `@Transactional(readOnly = true)`.
- **에러코드**: `COMPANY_IMAGE_VERIFICATION_NOT_FOUND("COMPANY-IMAGE-001", "직장 인증을 찾을 수 없습니다.", HttpStatus.NOT_FOUND)`.
- **응답**: status는 코드(name)+한글 라벨(description) 함께, 서류는 `imageKey` 미노출·`imageUrl`만 노출.
- 응답 언어 한국어. 커밋 트레일러: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

## 참고: 확장할 기존 파일의 현재 시그니처

- `GetAdminCompanyVerificationsUseCase` — 현재 `getVerifications(page, size, status): AdminCompanyVerificationPage` 하나. (여기에 `getVerification(id)` 추가)
- `GetAdminCompanyVerificationDao` — 현재 `findPage(offset, limit, status)`, `count(status)`. (여기에 `findDetailById(id)` 추가)
- `GetAdminCompanyVerificationsService` — dao·`CompanyVerificationImageUrlPort` 주입, `getVerifications` 구현. (여기에 `getVerification` 추가)
- `GetAdminCompanyVerificationDaoImpl`(infra) — 이미 `QCompanyImageVerificationEntity`/`QUserDetailEntity`/`QUserEntity`/`Projections`/`JPAQueryFactory` import 보유. `UserDetailEntity`에 `companyName`/`companyEmail`/`job` 필드 존재.
- `AdminCompanyVerificationController`(api) — `@RequestMapping("/admin/v1/company-image-verifications")`, `getAdminCompanyVerificationsUseCase` 주입, `@GetMapping` 목록 하나.
- 테스트 픽스처: `UserEntityFixture.create(providerId, email)`, `UserDetailEntityFixture.create(userId, nickname, job=, companyEmail=, companyName=)`, `CompanyImageVerificationEntityFixture.create(userId, imageKey, status)`. presign 페이크는 `TestFileStorageConfig`의 `CompanyVerificationImageUrlPort`(→ `https://presigned.test/<imageKey>`).

---

## Task 1: meeple-admin — 상세 read model·에러코드·dao/UseCase/Service 메서드

**Files:**
- Create: `meeple-admin/src/main/kotlin/com/org/meeple/admin/companyverification/query/dto/AdminCompanyVerificationDetailView.kt`
- Modify: `meeple-admin/src/main/kotlin/com/org/meeple/admin/common/error/AdminErrorCode.kt`
- Modify: `meeple-admin/src/main/kotlin/com/org/meeple/admin/companyverification/query/dao/GetAdminCompanyVerificationDao.kt`
- Modify: `meeple-admin/src/main/kotlin/com/org/meeple/admin/companyverification/query/service/port/in/GetAdminCompanyVerificationsUseCase.kt`
- Modify: `meeple-admin/src/main/kotlin/com/org/meeple/admin/companyverification/query/service/GetAdminCompanyVerificationsService.kt`

**Interfaces:**
- Produces:
  - `AdminCompanyVerificationDetailView(id: Long, userId: Long, nickname: String?, email: String?, status: CompanyImageVerificationStatus, createdAt: LocalDateTime?, imageKey: String, companyName: String?, companyEmail: String?, job: String?, imageUrl: String?)` + imageUrl 제외 10-arg 보조 생성자.
  - `AdminErrorCode.COMPANY_IMAGE_VERIFICATION_NOT_FOUND`
  - `GetAdminCompanyVerificationDao.findDetailById(id: Long): AdminCompanyVerificationDetailView?`
  - `GetAdminCompanyVerificationsUseCase.getVerification(id: Long): AdminCompanyVerificationDetailView`

- [ ] **Step 1: 상세 read model DTO 작성**

`AdminCompanyVerificationDetailView.kt`:

```kotlin
package com.org.meeple.admin.companyverification.query.dto

import com.org.meeple.common.user.CompanyImageVerificationStatus
import java.time.LocalDateTime

/**
 * 어드민 회사 이미지 인증 상세 read model. 목록 필드 + 사용자가 주장한 직장 정보(companyName·companyEmail·job).
 * dao는 [imageKey]까지 채우고 [imageUrl]은 null로 둔다. 서비스가 presign 결과로 [imageUrl]을 채운다.
 * (QueryDSL Projections.constructor가 imageUrl 없이 투영할 수 있도록 10-arg 보조 생성자를 둔다)
 */
data class AdminCompanyVerificationDetailView(
	val id: Long,
	val userId: Long,
	val nickname: String?,
	val email: String?,
	val status: CompanyImageVerificationStatus,
	val createdAt: LocalDateTime?,
	val imageKey: String,
	val companyName: String?,
	val companyEmail: String?,
	val job: String?,
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
	) : this(id, userId, nickname, email, status, createdAt, imageKey, companyName, companyEmail, job, null)
}
```

- [ ] **Step 2: AdminErrorCode에 상수 추가**

`AdminErrorCode.kt`의 `REPORT_NOT_FOUND(...)` 줄 뒤에 추가:

```kotlin
	// 코드 문자열은 기존 신고 에러(REPORT-001)와 동일하게 유지해 응답 계약을 보존한다.
	REPORT_NOT_FOUND("REPORT-001", "신고를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	COMPANY_IMAGE_VERIFICATION_NOT_FOUND("COMPANY-IMAGE-001", "직장 인증을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
```

(기존 `REPORT_NOT_FOUND(...)` 줄의 끝 콤마 뒤에 새 상수 줄을 추가한다. enum 마지막 상수의 콤마/세미콜론 유무는 기존 파일 형식을 따른다 — 현재 마지막이 콤마로 끝나므로 그대로 콤마 유지.)

- [ ] **Step 3: dao에 findDetailById 추가**

`GetAdminCompanyVerificationDao.kt`를 아래로 만든다(기존 메서드 유지 + 상세 메서드·import 추가):

```kotlin
package com.org.meeple.admin.companyverification.query.dao

import com.org.meeple.admin.companyverification.query.dto.AdminCompanyVerificationDetailView
import com.org.meeple.admin.companyverification.query.dto.AdminCompanyVerificationViews
import com.org.meeple.common.user.CompanyImageVerificationStatus

/** 어드민 회사 이미지 인증 조회 dao(query out-port). */
interface GetAdminCompanyVerificationDao {

	/** [status](없으면 전체)를 최신순(id desc)으로 [offset]부터 [limit]건 조회한다. */
	fun findPage(offset: Long, limit: Int, status: CompanyImageVerificationStatus?): AdminCompanyVerificationViews

	/** (soft delete 제외) [status](없으면 전체) 조건 전체 개수. (페이징 메타데이터 계산용) */
	fun count(status: CompanyImageVerificationStatus?): Long

	/** 인증 상세를 [id]로 조회한다. 없거나 soft-delete면 null. */
	fun findDetailById(id: Long): AdminCompanyVerificationDetailView?
}
```

- [ ] **Step 4: UseCase에 getVerification 추가**

`GetAdminCompanyVerificationsUseCase.kt`를 아래로 만든다:

```kotlin
package com.org.meeple.admin.companyverification.query.service.port.`in`

import com.org.meeple.admin.companyverification.query.dto.AdminCompanyVerificationDetailView
import com.org.meeple.admin.companyverification.query.dto.AdminCompanyVerificationPage
import com.org.meeple.common.user.CompanyImageVerificationStatus

/** 어드민 회사 이미지 인증 조회 유스케이스. */
interface GetAdminCompanyVerificationsUseCase {

	/** 회사 이미지 인증을 최신순으로 [page](0부터)·[size] 단위 페이징 조회한다. [status] 생략 시 전체. */
	fun getVerifications(page: Int, size: Int, status: CompanyImageVerificationStatus?): AdminCompanyVerificationPage

	/** 회사 이미지 인증 상세를 [id]로 조회한다. 없으면 예외를 던진다. */
	fun getVerification(id: Long): AdminCompanyVerificationDetailView
}
```

- [ ] **Step 5: Service에 getVerification 구현**

`GetAdminCompanyVerificationsService.kt`의 import 블록에 추가:

```kotlin
import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.companyverification.query.dto.AdminCompanyVerificationDetailView
```

`getVerifications(...)` 함수 닫는 중괄호 뒤, `companion object` 앞에 메서드 추가:

```kotlin
	override fun getVerification(id: Long): AdminCompanyVerificationDetailView {
		val view: AdminCompanyVerificationDetailView = getAdminCompanyVerificationDao.findDetailById(id)
			?: throw AdminException(
				AdminErrorCode.COMPANY_IMAGE_VERIFICATION_NOT_FOUND,
				"직장 인증을 찾을 수 없습니다: $id",
			)
		return view.copy(imageUrl = companyVerificationImageUrlPort.presignedGetUrl(view.imageKey))
	}
```

- [ ] **Step 6: 컴파일 확인 + core 비의존 확인**

Run: `./gradlew :meeple-admin:compileKotlin -q`
Expected: 성공(exit 0). (infra/api는 아직 findDetailById 미구현이라 전체 빌드는 Task 3 후 성공)

Run: `grep -rn "com.org.meeple.core" meeple-admin/src --include="*.kt" | wc -l | tr -d ' '`
Expected: `0`

- [ ] **Step 7: 커밋**

```bash
git add meeple-admin/src/main/kotlin/com/org/meeple/admin/companyverification/query/dto/AdminCompanyVerificationDetailView.kt \
        meeple-admin/src/main/kotlin/com/org/meeple/admin/common/error/AdminErrorCode.kt \
        meeple-admin/src/main/kotlin/com/org/meeple/admin/companyverification/query/dao/GetAdminCompanyVerificationDao.kt \
        "meeple-admin/src/main/kotlin/com/org/meeple/admin/companyverification/query/service/port/in/GetAdminCompanyVerificationsUseCase.kt" \
        meeple-admin/src/main/kotlin/com/org/meeple/admin/companyverification/query/service/GetAdminCompanyVerificationsService.kt
git commit -m "feat(admin): 회사 이미지 인증 상세 조회 read model·포트·서비스 추가

상세 read model(목록 필드 + 주장 직장정보)·dao findDetailById·UseCase getVerification·
서비스 구현(presign + 없으면 AdminException). COMPANY-IMAGE-001 에러코드 추가.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: meeple-infra — findDetailById QueryDSL 구현

**Files:**
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/user/query/GetAdminCompanyVerificationDaoImpl.kt`

**Interfaces:**
- Consumes (Task 1): `GetAdminCompanyVerificationDao.findDetailById`, `AdminCompanyVerificationDetailView`(10-arg 보조 생성자).

- [ ] **Step 1: import 추가**

`GetAdminCompanyVerificationDaoImpl.kt`의 import 블록에 추가:

```kotlin
import com.org.meeple.admin.companyverification.query.dto.AdminCompanyVerificationDetailView
```

- [ ] **Step 2: findDetailById 메서드 구현**

`count(...)` 메서드와 `statusEq(...)` private 헬퍼 사이(또는 클래스 내 적절한 위치)에 메서드 추가:

```kotlin
	override fun findDetailById(id: Long): AdminCompanyVerificationDetailView? {
		val verification: QCompanyImageVerificationEntity = QCompanyImageVerificationEntity.companyImageVerificationEntity
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val user: QUserEntity = QUserEntity.userEntity

		return queryFactory
			.select(
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
				),
			)
			.from(verification)
			.leftJoin(detail).on(detail.userId.eq(verification.userId))
			.leftJoin(user).on(user.id.eq(verification.userId))
			.where(verification.id.eq(id))
			.fetchOne()
	}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :meeple-infra:compileKotlin -q`
Expected: 성공(exit 0).

- [ ] **Step 4: 커밋**

```bash
git add meeple-infra/src/main/kotlin/com/org/meeple/infra/user/query/GetAdminCompanyVerificationDaoImpl.kt
git commit -m "feat(admin): 회사 이미지 인증 상세 findDetailById QueryDSL 구현

user_details(nickname·companyName·companyEmail·job)·users(email)를 조인해 id로 단건 조회한다.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: meeple-api — 상세 응답 DTO + 컨트롤러 엔드포인트

**Files:**
- Create: `meeple-api/src/main/kotlin/com/org/meeple/api/admin/response/AdminCompanyVerificationDetailResponse.kt`
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/api/admin/AdminCompanyVerificationController.kt`

**Interfaces:**
- Consumes (Task 1): `GetAdminCompanyVerificationsUseCase.getVerification(id)`, `AdminCompanyVerificationDetailView`.
- Produces: `GET /admin/v1/company-image-verifications/{id}`, `AdminCompanyVerificationDetailResponse.of(view)`.

- [ ] **Step 1: 상세 응답 DTO 작성**

`response/AdminCompanyVerificationDetailResponse.kt`:

```kotlin
package com.org.meeple.api.admin.response

import com.org.meeple.admin.companyverification.query.dto.AdminCompanyVerificationDetailView
import java.time.LocalDateTime

/**
 * 어드민 회사 이미지 인증 상세 응답. 목록 필드 + 사용자가 주장한 직장 정보(companyName·companyEmail·job).
 * status는 코드(name)와 한글 라벨(description)을 함께 노출하고, 서류는 열람용 presigned URL([imageUrl])만 노출한다.
 */
data class AdminCompanyVerificationDetailResponse(
	val id: Long,
	val userId: Long,
	val status: String,
	val statusLabel: String,
	val createdAt: LocalDateTime?,
	val nickname: String?,
	val email: String?,
	val companyName: String?,
	val companyEmail: String?,
	val job: String?,
	val imageUrl: String?,
) {
	companion object {
		fun of(view: AdminCompanyVerificationDetailView): AdminCompanyVerificationDetailResponse =
			AdminCompanyVerificationDetailResponse(
				id = view.id,
				userId = view.userId,
				status = view.status.name,
				statusLabel = view.status.description,
				createdAt = view.createdAt,
				nickname = view.nickname,
				email = view.email,
				companyName = view.companyName,
				companyEmail = view.companyEmail,
				job = view.job,
				imageUrl = view.imageUrl,
			)
	}
}
```

- [ ] **Step 2: 컨트롤러에 상세 엔드포인트 추가**

`AdminCompanyVerificationController.kt`의 import 블록에 추가:

```kotlin
import com.org.meeple.api.admin.response.AdminCompanyVerificationDetailResponse
import org.springframework.web.bind.annotation.PathVariable
```

`verifications(...)` 목록 메서드 뒤(클래스 닫는 중괄호 앞)에 메서드 추가:

```kotlin
	@Operation(
		summary = "회사 이미지 인증 상세 조회",
		description = "직장 서류 인증 한 건을 id로 조회한다. 없으면 404(COMPANY-IMAGE-001). imageUrl은 일정 시간 유효한 열람용 presigned URL이다.",
	)
	@GetMapping("/{id}")
	fun verification(
		@PathVariable id: Long,
	): ApiResponse<AdminCompanyVerificationDetailResponse> =
		ApiResponse.success(
			AdminCompanyVerificationDetailResponse.of(
				getAdminCompanyVerificationsUseCase.getVerification(id),
			),
		)
```

- [ ] **Step 3: 전체 컴파일 확인**

Run: `./gradlew :meeple-api:compileKotlin -q`
Expected: 성공(exit 0).

- [ ] **Step 4: 커밋**

```bash
git add meeple-api/src/main/kotlin/com/org/meeple/api/admin/response/AdminCompanyVerificationDetailResponse.kt \
        meeple-api/src/main/kotlin/com/org/meeple/api/admin/AdminCompanyVerificationController.kt
git commit -m "feat(admin): 회사 이미지 인증 상세 조회 컨트롤러·응답 DTO 추가

GET /admin/v1/company-image-verifications/{id}. 응답은 목록 필드 + 주장 직장정보(companyName·companyEmail·job)·imageUrl.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: E2E 테스트

**Files:**
- Test: `meeple-api/src/test/kotlin/com/org/meeple/api/admin/AdminCompanyVerificationDetailE2ETest.kt`

**Interfaces:**
- Consumes: `GET /admin/v1/company-image-verifications/{id}`, 픽스처(`UserEntityFixture`, `UserDetailEntityFixture`, `CompanyImageVerificationEntityFixture`), `IntegrationUtil`, `adminAccessTokenFor(id)`, presign 페이크(`TestFileStorageConfig`, 기존).

- [ ] **Step 1: E2E 테스트 작성**

`AdminCompanyVerificationDetailE2ETest.kt`:

```kotlin
package com.org.meeple.api.admin

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.user.CompanyImageVerificationStatus
import com.org.meeple.infra.fixture.CompanyImageVerificationEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.user.command.entity.QCompanyImageVerificationEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity

/**
 * `GET /admin/v1/company-image-verifications/{id}` E2E 테스트.
 * 직장 서류 인증 상세(목록 필드 + 주장 직장정보 + 열람용 imageUrl)를 200으로 반환하고,
 * 없는 id는 404(COMPANY-IMAGE-001)를 반환하는지 검증한다.
 * (presigned URL은 TestFileStorageConfig의 페이크로 대체 — https://presigned.test/<imageKey>)
 */
class AdminCompanyVerificationDetailE2ETest : AbstractIntegrationSupport({

	describe("GET /admin/v1/company-image-verifications/{id}") {

		it("인증 상세를 반환한다 (200)") {
			val userId: Long = IntegrationUtil.persist(
				UserEntityFixture.create(providerId = "civ-detail", email = "civd@test.com"),
			).id!!
			IntegrationUtil.persist(
				UserDetailEntityFixture.create(
					userId = userId,
					nickname = "인증유저",
					job = "백엔드 개발자",
					companyEmail = "hr@meeple.com",
					companyName = "미플",
				),
			)
			val id: Long = IntegrationUtil.persist(
				CompanyImageVerificationEntityFixture.create(
					userId = userId,
					imageKey = "detail-key",
					status = CompanyImageVerificationStatus.APPROVED,
				),
			).id!!

			get("/admin/v1/company-image-verifications/$id") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("success", true)
				body("data.id", id.toInt())
				body("data.userId", userId.toInt())
				body("data.status", "APPROVED")
				body("data.statusLabel", "승인")
				body("data.nickname", "인증유저")
				body("data.email", "civd@test.com")
				body("data.companyName", "미플")
				body("data.companyEmail", "hr@meeple.com")
				body("data.job", "백엔드 개발자")
				body("data.imageUrl", "https://presigned.test/detail-key")
			}
		}

		it("없는 id면 404다 (COMPANY-IMAGE-001)") {
			get("/admin/v1/company-image-verifications/999999") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(404)
				body("success", false)
				body("error.code", "COMPANY-IMAGE-001")
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

- [ ] **Step 2: E2E 실행**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.admin.AdminCompanyVerificationDetailE2ETest" -q`
Expected: PASS (2개 케이스 모두 통과). Task 1~3 구현이 완료됐으므로 통과해야 한다. 실패 시:
- 원인이 **테스트 코드**(픽스처 시그니처·단언값·정리 누락)이면 테스트를 고친다.
- 원인이 **프로덕션 코드**(Task 1~3 버그)이면 최소 수정하되 보고서에 무엇을 왜 바꿨는지 명시한다. 특히 **404가 500으로 나오면** `AdminException`이 잡히는지(핸들러 우선순위)와 에러코드 매핑을 점검한다. 확신이 안 서면 BLOCKED.

- [ ] **Step 3: 전체 빌드·테스트 확인**

Run: `./gradlew build -q`
Expected: 성공(exit 0). (목록 E2E·report E2E 포함 기존 테스트 회귀 없음)

- [ ] **Step 4: 커밋**

```bash
git add meeple-api/src/test/kotlin/com/org/meeple/api/admin/AdminCompanyVerificationDetailE2ETest.kt
git commit -m "test(admin): 회사 이미지 인증 상세 조회 E2E 추가

200 상세 필드(주장 직장정보·imageUrl)·404(COMPANY-IMAGE-001)를 검증한다.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review 결과

- **Spec coverage**: 엔드포인트(Task 3)·상세 필드+주장 직장정보(Task 1 DTO, 2 daoImpl, 3 응답, 4 테스트)·404 에러코드(Task 1 AdminErrorCode/Service, 4 테스트)·presigned URL 재사용(Task 1 Service)·admin core 비의존(Task 1 Step 6) 모두 커버.
- **Placeholder scan**: 모든 코드 스텝에 실제 코드, TODO/TBD 없음.
- **Type consistency**: `getVerification(id): AdminCompanyVerificationDetailView` / `findDetailById(id): AdminCompanyVerificationDetailView?` / `AdminCompanyVerificationDetailView`(11-arg 본 생성자 + 10-arg 보조 생성자, Projections는 10-arg에 바인딩) / `COMPANY_IMAGE_VERIFICATION_NOT_FOUND` 명칭이 Task 1~4에서 일치. 컨트롤러 응답 매핑은 `imageUrl` 노출·`imageKey` 미노출.
