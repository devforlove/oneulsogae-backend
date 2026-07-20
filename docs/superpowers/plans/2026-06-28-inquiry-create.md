# 문의하기(inquiry) 생성 슬라이스 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 고객센터 1:1 문의를 서버에 저장하는 `inquiry` 도메인의 생성(command) 슬라이스를 추가한다.

**Architecture:** 헥사고날(Ports & Adapters) + CQRS. `oneulsogae-common`에 enum, `oneulsogae-core`에 도메인·포트·서비스, `oneulsogae-infra`에 엔티티·어댑터, `oneulsogae-api`에 컨트롤러·DTO. notice 도메인을 그대로 참조한다. 조회/답변 API는 만들지 않되 엔티티에 `status`/`answer` 컬럼만 선반영한다.

**Tech Stack:** Kotlin 2.2.21 / JVM 21, Spring Boot 4.0.6, Spring Data JPA, MySQL, Kotest(DescribeSpec) 유닛, RestAssured + Testcontainers E2E.

## Global Constraints

- 응답·코드 주석·커밋 메시지는 한국어. **`meeple-backend`만 수정**(프론트엔드 미수정).
- 타입 명시: 변수·반환 타입·람다 파라미터 타입 생략 금지.
- 도메인 검증은 도메인 모델의 `validate…` 함수로 캡슐화(서비스에 `if…throw` 나열 금지).
- 명령 서비스 `@Transactional`(인자 없음). out-port는 `Save…Port`.
- enum 컬럼은 `@Enumerated(EnumType.STRING)`. 모든 엔티티는 `BaseEntity` 상속 + soft delete `@SQLRestriction("deleted_at is null")`.
- 컨트롤러는 in-port `UseCase` 주입, 응답은 `ApiResponse.success(...)` 래핑, 인증 사용자는 `@LoginUser user: AuthUser` → `user.id`.
- 패키지 루트 `com.org.oneulsogae`. 들여쓰기는 탭(기존 파일과 동일).
- 커밋 메시지 형식 `<type>(inquiry): <설명>`, 끝에 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- 카테고리 enum 값은 프론트 `INQUIRY_CATEGORIES`와 정확히 일치: `ACCOUNT`, `PAYMENT`, `MATCHING`, `REPORT`, `ETC`. message 길이 10~1000.

## File Structure

생성할 파일:

- `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/inquiry/InquiryCategory.kt` — 문의 유형 enum
- `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/inquiry/InquiryStatus.kt` — 문의 상태 enum
- `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/inquiry/InquiryErrorCode.kt` — 도메인 에러 코드
- `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/inquiry/command/domain/Inquiry.kt` — 도메인 모델 + 검증
- `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/inquiry/command/application/port/in/CreateInquiryUseCase.kt`
- `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/inquiry/command/application/port/in/command/CreateInquiryCommand.kt`
- `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/inquiry/command/application/port/out/SaveInquiryPort.kt`
- `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/inquiry/command/application/CreateInquiryService.kt`
- `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/inquiry/command/entity/InquiryEntity.kt`
- `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/inquiry/command/repository/InquiryJpaRepository.kt`
- `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/inquiry/command/mapper/InquiryMapper.kt`
- `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/inquiry/command/adapter/InquiryAdapter.kt`
- `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/inquiry/InquiryController.kt`
- `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/inquiry/request/CreateInquiryRequest.kt`
- `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/inquiry/response/CreateInquiryResponse.kt`

테스트 파일:

- `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/inquiry/InquiryTest.kt` — 도메인 유닛(Kotest)
- `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/inquiry/InquiryCreateE2ETest.kt` — 생성 E2E

---

### Task 1: 공통 enum + 도메인 모델 + 검증 (유닛 테스트)

**Files:**
- Create: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/inquiry/InquiryCategory.kt`
- Create: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/inquiry/InquiryStatus.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/inquiry/InquiryErrorCode.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/inquiry/command/domain/Inquiry.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/inquiry/InquiryTest.kt`

**Interfaces:**
- Produces:
  - `enum InquiryCategory(val description: String)`: `ACCOUNT`, `PAYMENT`, `MATCHING`, `REPORT`, `ETC`
  - `enum InquiryStatus(val description: String)`: `PENDING`, `ANSWERED`
  - `enum InquiryErrorCode : ErrorCode`: `INVALID_EMAIL`, `MESSAGE_TOO_SHORT`, `MESSAGE_TOO_LONG`
  - `data class Inquiry(id: Long = 0, userId: Long, category: InquiryCategory, email: String, message: String, status: InquiryStatus = PENDING, answer: String? = null, answeredAt: LocalDateTime? = null)`
  - `Inquiry.Companion.create(userId: Long, category: InquiryCategory, email: String, message: String): Inquiry` — 검증 후 `status = PENDING`으로 생성

- [ ] **Step 1: 공통 enum 두 개를 작성한다**

`oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/inquiry/InquiryCategory.kt`:
```kotlin
package com.org.oneulsogae.common.inquiry

/** 고객센터 문의 유형. 프론트 INQUIRY_CATEGORIES와 1:1로 대응한다. */
enum class InquiryCategory(val description: String) {

	ACCOUNT("계정·로그인"),
	PAYMENT("결제·코인"),
	MATCHING("매칭·채팅"),
	REPORT("신고·이용 제한"),
	ETC("기타 문의"),
}
```

`oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/inquiry/InquiryStatus.kt`:
```kotlin
package com.org.oneulsogae.common.inquiry

/** 문의 처리 상태. */
enum class InquiryStatus(val description: String) {

	/** 접수됨. 운영자 답변 대기 중. */
	PENDING("대기"),

	/** 운영자 답변 완료. */
	ANSWERED("답변완료"),
}
```

- [ ] **Step 2: 도메인 에러 코드를 작성한다**

`oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/inquiry/InquiryErrorCode.kt`:
```kotlin
package com.org.oneulsogae.core.inquiry

import com.org.oneulsogae.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

enum class InquiryErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	INVALID_EMAIL("INQ-001", "유효한 이메일 형식이 아닙니다.", HttpStatus.BAD_REQUEST),
	MESSAGE_TOO_SHORT("INQ-002", "문의 내용은 최소 10자 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
	MESSAGE_TOO_LONG("INQ-003", "문의 내용은 1000자 이하여야 합니다.", HttpStatus.BAD_REQUEST),
}
```

- [ ] **Step 3: 실패하는 도메인 유닛 테스트를 작성한다**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/inquiry/InquiryTest.kt`:
```kotlin
package com.org.oneulsogae.domain.inquiry

import com.org.oneulsogae.common.inquiry.InquiryCategory
import com.org.oneulsogae.common.inquiry.InquiryStatus
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.inquiry.InquiryErrorCode
import com.org.oneulsogae.core.inquiry.command.domain.Inquiry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class InquiryTest : DescribeSpec({

	describe("Inquiry.create") {

		it("정상 입력이면 PENDING 상태로 생성된다") {
			val inquiry: Inquiry = Inquiry.create(
				userId = 1L,
				category = InquiryCategory.ACCOUNT,
				email = "user@test.com",
				message = "로그인이 안 됩니다.",
			)

			inquiry.status shouldBe InquiryStatus.PENDING
			inquiry.answer shouldBe null
			inquiry.answeredAt shouldBe null
		}

		it("이메일 형식이 아니면 INVALID_EMAIL을 던진다") {
			val exception: BusinessException = shouldThrow {
				Inquiry.create(1L, InquiryCategory.ACCOUNT, "invalid-email", "정상적인 문의 내용입니다.")
			}

			exception.errorCode shouldBe InquiryErrorCode.INVALID_EMAIL
		}

		it("문의 내용이 10자 미만이면 MESSAGE_TOO_SHORT를 던진다") {
			val exception: BusinessException = shouldThrow {
				Inquiry.create(1L, InquiryCategory.ACCOUNT, "user@test.com", "012345678")
			}

			exception.errorCode shouldBe InquiryErrorCode.MESSAGE_TOO_SHORT
		}

		it("문의 내용이 1000자를 초과하면 MESSAGE_TOO_LONG을 던진다") {
			val exception: BusinessException = shouldThrow {
				Inquiry.create(1L, InquiryCategory.ACCOUNT, "user@test.com", "가".repeat(1001))
			}

			exception.errorCode shouldBe InquiryErrorCode.MESSAGE_TOO_LONG
		}

		it("경계값(10자·1000자)은 통과한다") {
			Inquiry.create(1L, InquiryCategory.ETC, "user@test.com", "가".repeat(10)).message.length shouldBe 10
			Inquiry.create(1L, InquiryCategory.ETC, "user@test.com", "가".repeat(1000)).message.length shouldBe 1000
		}
	}
})
```

- [ ] **Step 4: 테스트가 컴파일 실패(미정의)하는지 확인한다**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.inquiry.InquiryTest"`
Expected: 컴파일 실패 — `Inquiry` / `create` 미정의 (unresolved reference).

- [ ] **Step 5: 도메인 모델을 작성한다**

`oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/inquiry/command/domain/Inquiry.kt`:
```kotlin
package com.org.oneulsogae.core.inquiry.command.domain

import com.org.oneulsogae.common.inquiry.InquiryCategory
import com.org.oneulsogae.common.inquiry.InquiryStatus
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.inquiry.InquiryErrorCode
import java.time.LocalDateTime

private const val MESSAGE_MIN_LENGTH: Int = 10
private const val MESSAGE_MAX_LENGTH: Int = 1000
private val EMAIL_REGEX: Regex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/**
 * 고객센터 1:1 문의 도메인 모델. (명령 측 — 생성/저장에 쓴다)
 * 접수 시각은 별도 필드 없이 영속성의 created_at(JPA Auditing)으로 갈음한다.
 * status/answer/answeredAt는 추후 운영자 답변용으로 선반영했고, 생성 시에는 PENDING·null이다.
 * 영속성은 [com.org.oneulsogae.infra.inquiry.command.entity.InquiryEntity]가 담당한다.
 */
data class Inquiry(
	val id: Long = 0,
	val userId: Long,
	val category: InquiryCategory,
	val email: String,
	val message: String,
	val status: InquiryStatus = InquiryStatus.PENDING,
	val answer: String? = null,
	val answeredAt: LocalDateTime? = null,
) {
	companion object {

		/** 회원([userId])의 문의를 [category]·[email]·[message]로 접수한다. 입력을 검증한 뒤 PENDING으로 만든다. */
		fun create(
			userId: Long,
			category: InquiryCategory,
			email: String,
			message: String,
		): Inquiry {
			validateInquiry(email, message)
			return Inquiry(
				userId = userId,
				category = category,
				email = email,
				message = message,
			)
		}

		private fun validateInquiry(email: String, message: String) {
			if (!EMAIL_REGEX.matches(email)) {
				throw BusinessException(InquiryErrorCode.INVALID_EMAIL)
			}
			if (message.length < MESSAGE_MIN_LENGTH) {
				throw BusinessException(InquiryErrorCode.MESSAGE_TOO_SHORT)
			}
			if (message.length > MESSAGE_MAX_LENGTH) {
				throw BusinessException(InquiryErrorCode.MESSAGE_TOO_LONG)
			}
		}
	}
}
```

- [ ] **Step 6: 테스트 통과를 확인한다**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.inquiry.InquiryTest"`
Expected: PASS (5개 it 모두 통과).

- [ ] **Step 7: 커밋한다**

```bash
git add oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/inquiry \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/inquiry \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/inquiry
git commit -m "feat(inquiry): 문의 도메인 모델·enum·검증 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: core 포트 + 생성 서비스

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/inquiry/command/application/port/in/command/CreateInquiryCommand.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/inquiry/command/application/port/in/CreateInquiryUseCase.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/inquiry/command/application/port/out/SaveInquiryPort.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/inquiry/command/application/CreateInquiryService.kt`

**Interfaces:**
- Consumes (Task 1): `Inquiry`, `Inquiry.create(...)`, `InquiryCategory`
- Produces:
  - `data class CreateInquiryCommand(userId: Long, category: InquiryCategory, email: String, message: String)`
  - `interface CreateInquiryUseCase { fun create(command: CreateInquiryCommand): Inquiry }`
  - `interface SaveInquiryPort { fun save(inquiry: Inquiry): Inquiry }`
  - `class CreateInquiryService(saveInquiryPort: SaveInquiryPort) : CreateInquiryUseCase`

> 서비스는 위임만 하므로 별도 유닛 테스트를 두지 않는다(프로젝트 관례 — 서비스는 E2E로 검증). 이 태스크의 검증은 컴파일이다.

- [ ] **Step 1: command DTO를 작성한다**

`.../port/in/command/CreateInquiryCommand.kt`:
```kotlin
package com.org.oneulsogae.core.inquiry.command.application.port.`in`.command

import com.org.oneulsogae.common.inquiry.InquiryCategory

data class CreateInquiryCommand(
	val userId: Long,
	val category: InquiryCategory,
	val email: String,
	val message: String,
)
```

- [ ] **Step 2: in-port UseCase를 작성한다**

`.../port/in/CreateInquiryUseCase.kt`:
```kotlin
package com.org.oneulsogae.core.inquiry.command.application.port.`in`

import com.org.oneulsogae.core.inquiry.command.application.port.`in`.command.CreateInquiryCommand
import com.org.oneulsogae.core.inquiry.command.domain.Inquiry

interface CreateInquiryUseCase {

	fun create(command: CreateInquiryCommand): Inquiry
}
```

- [ ] **Step 3: out-port를 작성한다**

`.../port/out/SaveInquiryPort.kt`:
```kotlin
package com.org.oneulsogae.core.inquiry.command.application.port.out

import com.org.oneulsogae.core.inquiry.command.domain.Inquiry

interface SaveInquiryPort {

	fun save(inquiry: Inquiry): Inquiry
}
```

- [ ] **Step 4: 생성 서비스를 작성한다**

`.../application/CreateInquiryService.kt`:
```kotlin
package com.org.oneulsogae.core.inquiry.command.application

import com.org.oneulsogae.core.inquiry.command.application.port.`in`.CreateInquiryUseCase
import com.org.oneulsogae.core.inquiry.command.application.port.`in`.command.CreateInquiryCommand
import com.org.oneulsogae.core.inquiry.command.application.port.out.SaveInquiryPort
import com.org.oneulsogae.core.inquiry.command.domain.Inquiry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CreateInquiryService(
	private val saveInquiryPort: SaveInquiryPort,
) : CreateInquiryUseCase {

	override fun create(command: CreateInquiryCommand): Inquiry =
		saveInquiryPort.save(
			Inquiry.create(
				userId = command.userId,
				category = command.category,
				email = command.email,
				message = command.message,
			),
		)
}
```

- [ ] **Step 5: core 컴파일을 확인한다**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: 커밋한다**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/inquiry/command/application
git commit -m "feat(inquiry): 문의 생성 포트·서비스 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: infra 엔티티 + 매퍼 + 어댑터

**Files:**
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/inquiry/command/entity/InquiryEntity.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/inquiry/command/repository/InquiryJpaRepository.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/inquiry/command/mapper/InquiryMapper.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/inquiry/command/adapter/InquiryAdapter.kt`

**Interfaces:**
- Consumes (Task 1·2): `Inquiry`, `InquiryCategory`, `InquiryStatus`, `SaveInquiryPort`, infra `BaseEntity`
- Produces:
  - `class InquiryEntity(...) : BaseEntity()` — 테이블 `inquiries`, `var userId/category/email/message/status/answer/answeredAt`
  - `InquiryEntity.toDomain(): Inquiry`, `Inquiry.toEntity(): InquiryEntity`
  - `class InquiryAdapter(inquiryJpaRepository) : SaveInquiryPort` (`@Component`)
  - QueryDSL 생성물 `QInquiryEntity.inquiryEntity` (Task 4 E2E가 사용)

- [ ] **Step 1: JPA 엔티티를 작성한다**

`.../command/entity/InquiryEntity.kt`:
```kotlin
package com.org.oneulsogae.infra.inquiry.command.entity

import com.org.oneulsogae.common.inquiry.InquiryCategory
import com.org.oneulsogae.common.inquiry.InquiryStatus
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/**
 * 고객센터 1:1 문의 영속성 엔티티. 작성 회원(user_id)·유형(category)·답변 이메일(email)·내용(message)을 보관한다.
 * 접수 시각은 별도 컬럼 없이 [BaseEntity]의 created_at(JPA Auditing)으로 갈음한다.
 * status/answer/answered_at은 추후 운영자 답변용으로 선반영했고, 생성 시 PENDING·null이다.
 * 삭제는 soft delete(deleted_at)로 처리한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "inquiries",
	indexes = [
		Index(name = "idx_user_id", columnList = "user_id"),
	],
)
class InquiryEntity(
	@Column(name = "user_id", nullable = false)
	var userId: Long,

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, columnDefinition = "varchar(50)")
	var category: InquiryCategory,

	@Column(name = "email", nullable = false, length = 255)
	var email: String,

	@Column(name = "message", nullable = false, length = 1000)
	var message: String,

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	var status: InquiryStatus = InquiryStatus.PENDING,

	@Column(name = "answer", length = 2000)
	var answer: String? = null,

	@Column(name = "answered_at")
	var answeredAt: LocalDateTime? = null,
) : BaseEntity()
```

- [ ] **Step 2: JpaRepository를 작성한다**

`.../command/repository/InquiryJpaRepository.kt`:
```kotlin
package com.org.oneulsogae.infra.inquiry.command.repository

import com.org.oneulsogae.infra.inquiry.command.entity.InquiryEntity
import org.springframework.data.jpa.repository.JpaRepository

interface InquiryJpaRepository : JpaRepository<InquiryEntity, Long>
```

- [ ] **Step 3: 매퍼를 작성한다**

`.../command/mapper/InquiryMapper.kt`:
```kotlin
package com.org.oneulsogae.infra.inquiry.command.mapper

import com.org.oneulsogae.core.inquiry.command.domain.Inquiry
import com.org.oneulsogae.infra.inquiry.command.entity.InquiryEntity

fun InquiryEntity.toDomain(): Inquiry =
	Inquiry(
		id = id ?: 0,
		userId = userId,
		category = category,
		email = email,
		message = message,
		status = status,
		answer = answer,
		answeredAt = answeredAt,
	)

fun Inquiry.toEntity(): InquiryEntity =
	InquiryEntity(
		userId = userId,
		category = category,
		email = email,
		message = message,
		status = status,
		answer = answer,
		answeredAt = answeredAt,
	).also { if (id != 0L) it.id = id }
```

- [ ] **Step 4: 영속성 어댑터를 작성한다**

`.../command/adapter/InquiryAdapter.kt`:
```kotlin
package com.org.oneulsogae.infra.inquiry.command.adapter

import com.org.oneulsogae.core.inquiry.command.application.port.out.SaveInquiryPort
import com.org.oneulsogae.core.inquiry.command.domain.Inquiry
import com.org.oneulsogae.infra.inquiry.command.mapper.toDomain
import com.org.oneulsogae.infra.inquiry.command.mapper.toEntity
import com.org.oneulsogae.infra.inquiry.command.repository.InquiryJpaRepository
import org.springframework.stereotype.Component

/**
 * [InquiryEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 문의 저장 out-port([SaveInquiryPort])를 구현한다.
 */
@Component
class InquiryAdapter(
	private val inquiryJpaRepository: InquiryJpaRepository,
) : SaveInquiryPort {

	override fun save(inquiry: Inquiry): Inquiry =
		inquiryJpaRepository.save(inquiry.toEntity()).toDomain()
}
```

- [ ] **Step 5: infra 컴파일을 확인한다 (QInquiryEntity 생성 포함)**

Run: `./gradlew :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL. (`QInquiryEntity`가 build 디렉터리에 생성된다.)

- [ ] **Step 6: 커밋한다**

```bash
git add oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/inquiry
git commit -m "feat(inquiry): 문의 엔티티·매퍼·어댑터 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: api 컨트롤러 + DTO + E2E

**Files:**
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/inquiry/request/CreateInquiryRequest.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/inquiry/response/CreateInquiryResponse.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/inquiry/InquiryController.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/inquiry/InquiryCreateE2ETest.kt`

**Interfaces:**
- Consumes (Task 1·2·3): `CreateInquiryUseCase`, `CreateInquiryCommand`, `Inquiry`, `InquiryCategory`, `InquiryStatus`, `QInquiryEntity`, `AuthUser`, `@LoginUser`, `ApiResponse`
- Produces:
  - `data class CreateInquiryRequest(category, email, message)` + `toCommand(userId: Long): CreateInquiryCommand`
  - `data class CreateInquiryResponse(inquiryId: Long)` + `of(inquiry: Inquiry)`
  - `InquiryController` — `POST /inquiries/v1`

- [ ] **Step 1: 요청 DTO를 작성한다**

`.../inquiry/request/CreateInquiryRequest.kt`:
```kotlin
package com.org.oneulsogae.api.inquiry.request

import com.org.oneulsogae.common.inquiry.InquiryCategory
import com.org.oneulsogae.core.inquiry.command.application.port.`in`.command.CreateInquiryCommand
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class CreateInquiryRequest(
	@field:NotNull(message = "문의 유형은 필수입니다.")
	val category: InquiryCategory? = null,

	@field:NotBlank(message = "이메일은 필수입니다.")
	@field:Email(message = "유효한 이메일 형식이 아닙니다.")
	val email: String? = null,

	@field:NotBlank(message = "문의 내용은 필수입니다.")
	@field:Size(min = 10, max = 1000, message = "문의 내용은 10자 이상 1000자 이하여야 합니다.")
	val message: String? = null,
) {
	fun toCommand(userId: Long): CreateInquiryCommand =
		CreateInquiryCommand(
			userId = userId,
			category = category!!,
			email = email!!,
			message = message!!,
		)
}
```

- [ ] **Step 2: 응답 DTO를 작성한다**

`.../inquiry/response/CreateInquiryResponse.kt`:
```kotlin
package com.org.oneulsogae.api.inquiry.response

import com.org.oneulsogae.core.inquiry.command.domain.Inquiry

data class CreateInquiryResponse(
	val inquiryId: Long,
) {
	companion object {
		fun of(inquiry: Inquiry): CreateInquiryResponse =
			CreateInquiryResponse(inquiryId = inquiry.id)
	}
}
```

- [ ] **Step 3: 컨트롤러를 작성한다**

`.../inquiry/InquiryController.kt`:
```kotlin
package com.org.oneulsogae.api.inquiry

import com.org.oneulsogae.api.inquiry.request.CreateInquiryRequest
import com.org.oneulsogae.api.inquiry.response.CreateInquiryResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.inquiry.command.application.port.`in`.CreateInquiryUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "문의", description = "고객센터 문의하기 API. 1:1 문의를 접수한다.")
@RestController
@RequestMapping("/inquiries/v1")
class InquiryController(
	private val createInquiryUseCase: CreateInquiryUseCase,
) {

	@Operation(summary = "문의 생성", description = "로그인 사용자가 문의 유형·답변 이메일·내용으로 1:1 문의를 접수한다.")
	@PostMapping
	fun create(
		@LoginUser user: AuthUser,
		@RequestBody @Valid request: CreateInquiryRequest,
	): ApiResponse<CreateInquiryResponse> =
		ApiResponse.success(CreateInquiryResponse.of(createInquiryUseCase.create(request.toCommand(user.id))))
}
```

- [ ] **Step 4: 실패하는 E2E 테스트를 작성한다**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/inquiry/InquiryCreateE2ETest.kt`:
```kotlin
package com.org.oneulsogae.api.inquiry

import com.org.oneulsogae.common.inquiry.InquiryCategory
import com.org.oneulsogae.common.inquiry.InquiryStatus
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.inquiry.command.entity.InquiryEntity
import com.org.oneulsogae.infra.inquiry.command.entity.QInquiryEntity
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.notNullValue

class InquiryCreateE2ETest : AbstractIntegrationSupport({

	describe("POST /inquiries/v1") {

		context("로그인 사용자가 유효한 문의를 보내면") {
			it("PENDING 상태로 저장되고 inquiryId를 반환한다 (200)") {
				val userId = 1001L

				post("/inquiries/v1") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"category": "ACCOUNT", "email": "user@test.com", "message": "로그인이 안 됩니다. 도와주세요."}""")
				} expect {
					status(200)
					body("success", true)
					body("data.inquiryId", notNullValue())
				}

				val inquiry: QInquiryEntity = QInquiryEntity.inquiryEntity
				val saved: InquiryEntity = IntegrationUtil.getQuery()
					.selectFrom(inquiry)
					.where(inquiry.userId.eq(userId))
					.fetchOne()!!
				saved.status shouldBe InquiryStatus.PENDING
				saved.category shouldBe InquiryCategory.ACCOUNT
				saved.email shouldBe "user@test.com"
			}
		}

		context("문의 내용이 10자 미만이면") {
			it("400을 반환한다") {
				post("/inquiries/v1") {
					bearer(accessTokenFor(1002L))
					jsonBody("""{"category": "ETC", "email": "user@test.com", "message": "짧음"}""")
				} expect {
					status(400)
					body("success", false)
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				post("/inquiries/v1") {
					jsonBody("""{"category": "ETC", "email": "user@test.com", "message": "토큰 없이 보내는 문의입니다."}""")
				} expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QInquiryEntity.inquiryEntity)
	}
})
```

> 주의: 미인증 응답 코드(401)는 프로젝트의 `SecurityConfig`/인증 엔트리포인트 동작에 따른다. 실행 시 401이 아니라 403이면 기대값을 실제 동작에 맞춘다(둘 다 "인증 거부"의 정상 동작이며, 핵심 검증은 앞의 두 컨텍스트다).

- [ ] **Step 5: E2E가 실패(또는 컴파일 실패)하는지 확인한다**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.inquiry.InquiryCreateE2ETest"`
Expected: 처음엔 컨트롤러 미연결/엔드포인트 부재로 실패. (Step 1~3을 먼저 작성했다면 이 단계에서 바로 통과할 수 있으나, 반드시 한 번 실행해 통과를 눈으로 확인한다.)

- [ ] **Step 6: 전체 테스트로 통과를 확인한다**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.inquiry.InquiryCreateE2ETest"`
Expected: PASS (3개 컨텍스트 모두 통과 — 401이 다르면 Step 4 주의에 따라 조정 후 재실행).

- [ ] **Step 7: 모듈 전체 테스트로 회귀를 확인한다**

Run: `./gradlew :oneulsogae-api:test`
Expected: BUILD SUCCESSFUL (기존 테스트 포함 전부 통과).

- [ ] **Step 8: 커밋한다**

```bash
git add oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/inquiry \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/inquiry
git commit -m "feat(inquiry): 문의 생성 API·E2E 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 프론트엔드 연동 안내 (이 계획 범위 밖 — 백엔드는 수정하지 않음)

구현 완료 후 사용자에게 다음을 안내한다(직접 수정 금지):

- 엔드포인트: `POST /inquiries/v1` (인증 필요, `Authorization: Bearer`)
- 요청 body: `{ "category": "ACCOUNT" | "PAYMENT" | "MATCHING" | "REPORT" | "ETC", "email": string, "message": string }`
- 응답: `{ "success": true, "data": { "inquiryId": number }, "error": null }`
- 대응 위치: `meeple-frontend/src/domains/inquiry/data/datasources/remote`에 HttpClient 호출 데이터소스를 신설하고, `BrowserInquiryRepository`(로컬 mock)가 이를 사용하도록 교체. `InquiryCategory` 값이 동일하므로 매핑 변환은 불필요.

## 비포함 (YAGNI)

- 문의 목록/상세 조회(query) API, 운영자 답변 등록 API, 답변 작성자 식별 컬럼.
