# 비로그인 고객센터 문의 허용(하이브리드) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 `POST /inquiries/v1` 단일 엔드포인트가 토큰 유무를 모두 수용하도록 열어, 비로그인 사용자도 익명(`userId = null`)으로 고객센터 문의를 접수할 수 있게 한다.

**Architecture:** 헥사고날 레이어를 따라 `userId`를 nullable로 전파한다(domain → command → entity). 인증 경계는 `SecurityConfig`의 permitAll 화이트리스트와 컨트롤러의 nullable `@LoginUser`로 연다. 데이터 계층 변경(Task 1)과 인증 경계 개방(Task 2)을 분리한다.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4.0.6 / Spring Security / JPA / MySQL / Kotest / RestAssured(E2E, Testcontainers)

## Global Constraints

- 응답·주석·커밋 메시지는 한국어로 작성한다.
- `meeple-backend`만 수정한다. `meeple-frontend`는 건드리지 않는다(안내만).
- 타입을 명시한다(변수·반환·람다 파라미터). 표현식 본문 함수 포함.
- `LocalDateTime.now()` 직접 호출 금지(이번 변경에는 시각 로직 없음).
- 커밋 메시지 형식: `<type>(<domain>): <설명>`, 도메인은 `inquiry`.
- 헥사고날 경계 유지: Controller는 in-port UseCase 주입, 명령 측은 `command` 패키지.

---

### Task 1: nullable userId 데이터 모델 (domain → command → entity)

`userId`를 도메인·커맨드·엔티티 전반에서 nullable로 만든다. 인증 경계는 아직 열지 않는다(Task 2). 이 태스크 완료 시점에도 기존 동작(로그인 강제, 토큰 없으면 401)은 그대로 유지된다.

**Files:**
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/inquiry/command/domain/Inquiry.kt`
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/inquiry/command/application/port/in/command/CreateInquiryCommand.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/inquiry/command/entity/InquiryEntity.kt`
- Create: `docs/migration/inquiries_user_id_nullable.sql`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/domain/inquiry/InquiryTest.kt`

**Interfaces:**
- Produces:
  - `Inquiry.userId: Long?`
  - `Inquiry.create(userId: Long?, category: InquiryCategory, email: String, message: String): Inquiry`
  - `CreateInquiryCommand.userId: Long?`
  - `InquiryEntity.userId: Long?` (`@Column(name = "user_id", nullable = true)`)
- Consumes: 없음(첫 태스크).
- Note: `CreateInquiryService`는 변경 없음 — `command.userId`(이제 `Long?`)를 `Inquiry.create`에 그대로 전달하며, 양쪽 타입이 `Long?`로 일치한다. `InquiryMapper`도 변경 없음 — `userId = userId` 양방향 할당이 모두 `Long?` ↔ `Long?`가 되어 그대로 컴파일된다.

- [ ] **Step 1: 익명 생성 실패 테스트 작성**

`InquiryTest.kt`의 `describe("Inquiry.create")` 블록 안, 첫 `it` 다음에 케이스를 추가한다:

```kotlin
		it("userId가 null이면 익명 문의로 생성된다") {
			val inquiry: Inquiry = Inquiry.create(
				userId = null,
				category = InquiryCategory.ETC,
				email = "guest@test.com",
				message = "비로그인 상태에서 보내는 문의입니다.",
			)

			inquiry.userId shouldBe null
			inquiry.status shouldBe InquiryStatus.PENDING
		}
```

- [ ] **Step 2: 테스트가 컴파일 에러로 실패하는지 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.domain.inquiry.InquiryTest"`
Expected: 컴파일 실패 — `null` cannot be a value of a non-null type `Long` (`Inquiry.create`의 `userId`가 아직 `Long`).

- [ ] **Step 3: 도메인 모델을 nullable로 수정**

`Inquiry.kt`에서 `userId` 프로퍼티와 `create` 파라미터의 타입을 `Long?`로 바꾼다.

`data class Inquiry(` 의 필드:
```kotlin
	val userId: Long?,
```

`create` 함수 시그니처와 본문의 KDoc:
```kotlin
		/** 작성자([userId], 비로그인이면 null)의 문의를 [category]·[email]·[message]로 접수한다. 입력을 검증한 뒤 PENDING으로 만든다. */
		fun create(
			userId: Long?,
			category: InquiryCategory,
			email: String,
			message: String,
		): Inquiry {
```
(본문의 `Inquiry(userId = userId, ...)` 호출과 `validateInquiry`는 그대로 둔다 — `userId`는 검증 대상이 아니다.)

- [ ] **Step 4: 커맨드를 nullable로 수정**

`CreateInquiryCommand.kt`:
```kotlin
data class CreateInquiryCommand(
	val userId: Long?,
	val category: InquiryCategory,
	val email: String,
	val message: String,
)
```

- [ ] **Step 5: 엔티티 컬럼을 nullable로 수정**

`InquiryEntity.kt`의 `userId` 프로퍼티:
```kotlin
	@Column(name = "user_id", nullable = true)
	var userId: Long?,
```
(클래스 상단 KDoc의 "작성 회원(user_id)" 문구를 "작성 회원(user_id, 비로그인이면 null)"으로 갱신한다. `idx_user_id` 인덱스는 그대로 둔다.)

- [ ] **Step 6: DDL 마이그레이션 작성**

`docs/migration/inquiries_user_id_nullable.sql`:
```sql
-- 비로그인(익명) 고객센터 문의를 허용하기 위해 user_id를 nullable로 변경한다.
-- 로그인 사용자는 회원 ID, 비로그인 사용자는 NULL로 저장된다.
ALTER TABLE inquiries MODIFY user_id BIGINT NULL;
```

- [ ] **Step 7: 도메인 테스트 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.domain.inquiry.InquiryTest"`
Expected: PASS (신규 익명 케이스 포함 전체 통과).

- [ ] **Step 8: 전체 컴파일 확인**

Run: `./gradlew compileKotlin compileTestKotlin`
Expected: BUILD SUCCESSFUL (core/infra/api 모듈 모두 컴파일). `CreateInquiryService`·`InquiryMapper`·`CreateInquiryRequest.toCommand(Long)`는 `Long → Long?` 업캐스트로 변경 없이 통과한다.

- [ ] **Step 9: 커밋**

```bash
git add meeple-core/src/main/kotlin/com/org/meeple/core/inquiry meeple-infra/src/main/kotlin/com/org/meeple/infra/inquiry meeple-api/src/test/kotlin/com/org/meeple/domain/inquiry docs/migration/inquiries_user_id_nullable.sql
git commit -m "feat(inquiry): 문의 userId를 nullable로 변경해 익명 문의 데이터 모델 지원"
```

---

### Task 2: 비로그인 엔드포인트 개방 (SecurityConfig + Controller + E2E)

`/inquiries/v1`를 permitAll로 열고, 컨트롤러가 nullable `@LoginUser`를 받아 토큰 유무를 모두 수용하게 한다. E2E로 비로그인 접수 성공을 검증하고, 기존 "토큰 없으면 401" 케이스를 익명 접수 성공으로 갱신한다.

**Files:**
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/config/SecurityConfig.kt`
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/api/inquiry/InquiryController.kt`
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/api/inquiry/request/CreateInquiryRequest.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/api/inquiry/InquiryCreateE2ETest.kt`

**Interfaces:**
- Consumes (from Task 1): `CreateInquiryCommand.userId: Long?`, `Inquiry.create(userId: Long?, ...)`.
- Produces: `CreateInquiryRequest.toCommand(userId: Long?): CreateInquiryCommand`; `InquiryController.create(@LoginUser user: AuthUser?, ...)`; `/inquiries/v1` permitAll.

- [ ] **Step 1: E2E 테스트 갱신 (비로그인 접수 성공)**

`InquiryCreateE2ETest.kt`에서 기존 `context("인증 토큰이 없으면")` 블록을 아래로 교체한다(401 → 익명 접수 성공으로 의미 변경):

```kotlin
			context("인증 토큰이 없으면") {
				it("익명 문의로 저장되고(user_id NULL) inquiryId를 반환한다 (200)") {
					val response = post("/inquiries/v1") {
						jsonBody("""{"category": "ETC", "email": "guest@test.com", "message": "토큰 없이 보내는 문의입니다."}""")
					}
					response expect {
						status(200)
						body("success", true)
						body("data.inquiryId", notNullValue())
					}
					val inquiryId: Long = response.extract().path<Int>("data.inquiryId").toLong()

					val inquiry: QInquiryEntity = QInquiryEntity.inquiryEntity
					val saved: InquiryEntity = IntegrationUtil.getQuery()
						.selectFrom(inquiry)
						.where(inquiry.id.eq(inquiryId))
						.fetchOne()!!
					saved.userId shouldBe null
					saved.status shouldBe InquiryStatus.PENDING
					saved.email shouldBe "guest@test.com"
				}
			}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.inquiry.InquiryCreateE2ETest"`
Expected: FAIL — 토큰 없는 요청이 아직 401로 차단되어 `status(200)` 단언 실패.

- [ ] **Step 3: SecurityConfig에 permitAll 추가**

`SecurityConfig.kt`의 `authorizeHttpRequests` 블록에서 `.requestMatchers("/ws/chat/**").permitAll()` 다음 줄에 추가한다:
```kotlin
						// 비로그인 사용자도 고객센터 문의를 접수할 수 있도록 연다. (토큰 있으면 컨트롤러가 회원 ID로 귀속)
						.requestMatchers("/inquiries/v1").permitAll()
```

- [ ] **Step 4: 컨트롤러를 nullable 수신으로 수정**

`InquiryController.kt`의 `create` 메서드:
```kotlin
	@Operation(summary = "문의 생성", description = "로그인·비로그인 사용자가 문의 유형·답변 이메일·내용으로 1:1 문의를 접수한다. 비로그인은 익명으로 저장된다.")
	@PostMapping
	fun create(
		@LoginUser user: AuthUser?,
		@RequestBody @Valid request: CreateInquiryRequest,
	): ApiResponse<CreateInquiryResponse> =
		ApiResponse.success(CreateInquiryResponse.of(createInquiryUseCase.create(request.toCommand(user?.id))))
```

- [ ] **Step 5: 요청 DTO의 toCommand를 nullable 파라미터로 수정**

`CreateInquiryRequest.kt`:
```kotlin
	fun toCommand(userId: Long?): CreateInquiryCommand =
		CreateInquiryCommand(
			userId = userId,
			category = category!!,
			email = email!!,
			message = message!!,
		)
```

- [ ] **Step 6: E2E 테스트 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.inquiry.InquiryCreateE2ETest"`
Expected: PASS — 로그인 접수·짧은 메시지 400·비로그인 익명 접수 200 케이스 모두 통과.

- [ ] **Step 7: 인쿼리 관련 전체 테스트 재확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.inquiry.*" --tests "com.org.meeple.domain.inquiry.*"`
Expected: PASS.

- [ ] **Step 8: 커밋**

```bash
git add meeple-api/src/main/kotlin/com/org/meeple/config/SecurityConfig.kt meeple-api/src/main/kotlin/com/org/meeple/api/inquiry meeple-api/src/test/kotlin/com/org/meeple/api/inquiry
git commit -m "feat(inquiry): 비로그인 고객센터 문의 접수 허용(엔드포인트 permitAll + nullable 로그인 사용자)"
```

---

## 프론트엔드 안내 (직접 수정하지 않음)

구현 완료 후 사용자에게 전달할 내용 — `meeple-frontend`는 아래만 반영한다:
- 비로그인 상태에서도 동일하게 `POST /inquiries/v1` 호출(쿠키/토큰 없으면 익명 처리).
- 푸터 고객센터의 비로그인 차단 가드(로그인 리다이렉트·401 사전 차단) 제거.
- 요청 바디는 `category`·`email`·`message` 동일. 비로그인은 회신용 `email`을 직접 입력받아야 함.

## Self-Review

- **Spec coverage:** 인증 경계 개방(Task 2 Step 3·4) / nullable 전파(Task 1) / DDL(Task 1 Step 6) / 도메인 유닛(Task 1) / E2E(Task 2) / 방어장치 없음(추가 안 함) / 프론트 안내(문서 말미) — 스펙 전 항목 매핑됨.
- **Placeholder scan:** 모든 코드 스텝에 실제 코드·명령·기대 출력 포함. placeholder 없음.
- **Type consistency:** `userId: Long?`가 domain·command·entity·toCommand·controller(`user?.id`)에서 일관. `Inquiry.create`/`CreateInquiryCommand`/`InquiryEntity` 시그니처 Task 1·2 간 일치.
