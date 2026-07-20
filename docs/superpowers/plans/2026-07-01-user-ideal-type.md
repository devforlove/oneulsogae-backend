# 이상형 설정(Ideal Type) 저장·조회 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `user` 도메인에 이상형 설정 값을 서버에 영속화하는 CRUD API(`GET/PUT /users/v1/ideal-type`)를 추가한다.

**Architecture:** 헥사고날 CQRS. 도메인 검증은 `UserIdealType` 도메인 모델에 캡슐화하고, command(저장/upsert)와 query(조회)를 포트·서비스 단위로 분리한다. 이상형 값은 나중 매칭 쿼리에 바로 쓰기 좋은 이산 형태(나이/키는 숫자 경계, 나머지는 enum)로 저장한다. 매칭 연동은 이번 범위 밖.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4 / Spring Data JPA / MySQL / Kotest(도메인 유닛) / RestAssured + Testcontainers(E2E).

## Global Constraints

- 응답은 항상 한국어. `oneulsogae-backend`만 수정(프론트는 안내만).
- 타입 명시(변수·반환·람다 파라미터). 표현식 본문 함수 포함.
- 도메인 규칙은 도메인 모델의 `validate…` 함수로 캡슐화. 서비스에 `if…throw` 나열 금지.
- 조회 서비스 `@Transactional(readOnly = true)`, 명령 서비스 `@Transactional`. `Get…Port`/`Save…Port` 분리.
- enum 저장은 `@Enumerated(EnumType.STRING)` + `columnDefinition = "varchar(50)"` (기존 `UserDetailEntity` 관례).
- 신규 엔티티는 `BaseEntity` 상속 + `@SQLRestriction("deleted_at is null")` (기존 관례).
- 조회 구현 우선순위 ① Spring Data 파생 쿼리. 이상형 조회는 조인이 없으므로 QueryDSL 대신 command repository를 query daoImpl이 재사용한다(`infra` 내부 query→command 참조 허용).
- 와이어 계약: enum은 **enum name**(Jackson 기본), `null` = "상관없음", `ageRange`/`heightRange`는 2요소 배열. 나이 20~60, 키 150~195.

---

## File Structure

**신규 파일**
- `oneulsogae-common/.../common/user/DistancePreference.kt` — 거리 선호 enum
- `oneulsogae-core/.../user/command/domain/UserIdealType.kt` — 도메인 모델 + 검증
- `oneulsogae-core/.../user/command/application/port/in/SaveIdealTypeUseCase.kt`
- `oneulsogae-core/.../user/command/application/port/in/command/SaveIdealTypeCommand.kt`
- `oneulsogae-core/.../user/command/application/port/out/GetIdealTypePort.kt`
- `oneulsogae-core/.../user/command/application/port/out/SaveIdealTypePort.kt`
- `oneulsogae-core/.../user/command/application/SaveIdealTypeService.kt`
- `oneulsogae-core/.../user/query/dto/IdealTypeView.kt`
- `oneulsogae-core/.../user/query/dao/GetIdealTypeDao.kt`
- `oneulsogae-core/.../user/query/service/port/in/GetIdealTypeUseCase.kt`
- `oneulsogae-core/.../user/query/service/GetIdealTypeService.kt`
- `oneulsogae-infra/.../user/command/entity/UserIdealTypeEntity.kt`
- `oneulsogae-infra/.../user/command/mapper/UserIdealTypeMapper.kt`
- `oneulsogae-infra/.../user/command/repository/UserIdealTypeJpaRepository.kt`
- `oneulsogae-infra/.../user/command/adapter/UserIdealTypeCoreAdapter.kt`
- `oneulsogae-infra/.../user/query/GetIdealTypeDaoImpl.kt`
- `oneulsogae-api/.../api/user/IdealTypeController.kt`
- `oneulsogae-api/.../api/user/request/SaveIdealTypeRequest.kt`
- `oneulsogae-api/.../api/user/response/IdealTypeResponse.kt`
- `docs/migration/user_ideal_types.sql`
- 테스트: `oneulsogae-api/src/test/.../domain/user/UserIdealTypeTest.kt`(도메인 유닛), `oneulsogae-api/src/test/.../api/user/IdealTypeE2ETest.kt`

**수정 파일**
- `oneulsogae-core/.../user/UserErrorCode.kt` — 범위 검증 에러 코드 추가

패키지 접두: `com.org.oneulsogae`. 경로의 `.../`는 각 모듈 `src/main/kotlin/com/org/oneulsogae`.

---

## Task 1: 도메인 모델 + 거리 enum + 에러 코드 (Kotest 유닛)

**Files:**
- Create: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/user/DistancePreference.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/domain/UserIdealType.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/UserErrorCode.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/user/UserIdealTypeTest.kt`

**Interfaces:**
- Produces:
  - `enum class DistancePreference(val description: String) { SAME_REGION, ADJACENT_REGION }`
  - `data class UserIdealType(id: Long, userId: Long, ageMin: Int?, ageMax: Int?, heightMin: Int?, heightMax: Int?, maritalStatus: MaritalStatus?, smokingStatus: SmokingStatus?, drinkingStatus: DrinkingStatus?, religion: Religion?, distance: DistancePreference?)`
  - `UserIdealType.of(userId, ageMin, ageMax, heightMin, heightMax, maritalStatus, smokingStatus, drinkingStatus, religion, distance): UserIdealType` (companion)
  - `UserIdealType.update(ageMin, ageMax, heightMin, heightMax, maritalStatus, smokingStatus, drinkingStatus, religion, distance): UserIdealType` (id/userId 보존)
  - `UserErrorCode.INVALID_IDEAL_TYPE_RANGE`

- [ ] **Step 1: 거리 선호 enum 작성**

`oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/user/DistancePreference.kt`:

```kotlin
package com.org.oneulsogae.common.user

/**
 * 이상형 지역 거리 선호. 매칭 후보 탐색의 근접 지역 순회 깊이로 매핑된다.
 * null = "상관없음"(제한 없음)이므로 별도 값을 두지 않는다.
 */
enum class DistancePreference(val description: String) {

	/** 같은 활동지역(regionId 일치)만. */
	SAME_REGION("같은 지역만"),

	/** 같은 + 인접 지역까지. */
	ADJACENT_REGION("인접 지역까지"),
}
```

- [ ] **Step 2: 에러 코드 추가**

`oneulsogae-core/.../user/UserErrorCode.kt`의 `// 프로필(user_details)` 블록 마지막(`EMAIL_REQUIRED` 아래) 줄에 추가:

```kotlin
	// 이상형(user_ideal_types)
	INVALID_IDEAL_TYPE_RANGE("USER-019", "이상형 범위가 올바르지 않습니다. 최소값이 최대값보다 클 수 없고, 한쪽만 입력할 수 없습니다.", HttpStatus.BAD_REQUEST),
```

- [ ] **Step 3: 실패하는 도메인 유닛 테스트 작성**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/user/UserIdealTypeTest.kt`:

```kotlin
package com.org.oneulsogae.domain.user

import com.org.oneulsogae.common.user.DistancePreference
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.Religion
import com.org.oneulsogae.common.user.SmokingStatus
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.command.domain.UserIdealType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * [UserIdealType] 도메인 유닛 테스트.
 * 나이/키 범위 규칙(짝 존재·min ≤ max·경계)과 upsert 교체 시 식별자 보존을 검증한다.
 */
class UserIdealTypeTest : DescribeSpec({

	describe("of") {
		it("전 항목이 null이면 '상관없음' 이상형으로 생성된다") {
			val idealType: UserIdealType = UserIdealType.of(
				userId = 1L,
				ageMin = null, ageMax = null,
				heightMin = null, heightMax = null,
				maritalStatus = null, smokingStatus = null,
				drinkingStatus = null, religion = null, distance = null,
			)

			idealType.userId shouldBe 1L
			idealType.ageMin.shouldBeNull()
			idealType.distance.shouldBeNull()
		}

		it("정상 범위·enum이면 그대로 채워진다") {
			val idealType: UserIdealType = UserIdealType.of(
				userId = 1L,
				ageMin = 27, ageMax = 35,
				heightMin = 160, heightMax = 180,
				maritalStatus = MaritalStatus.SINGLE, smokingStatus = SmokingStatus.NON_SMOKER,
				drinkingStatus = null, religion = Religion.NONE, distance = DistancePreference.SAME_REGION,
			)

			idealType.ageMin shouldBe 27
			idealType.ageMax shouldBe 35
			idealType.distance shouldBe DistancePreference.SAME_REGION
		}

		it("나이 최소가 최대보다 크면 INVALID_IDEAL_TYPE_RANGE를 던진다") {
			val ex: BusinessException = shouldThrow {
				UserIdealType.of(
					userId = 1L,
					ageMin = 40, ageMax = 20,
					heightMin = null, heightMax = null,
					maritalStatus = null, smokingStatus = null,
					drinkingStatus = null, religion = null, distance = null,
				)
			}
			ex.errorCode shouldBe UserErrorCode.INVALID_IDEAL_TYPE_RANGE
		}

		it("나이 범위 한쪽만 주어지면 INVALID_IDEAL_TYPE_RANGE를 던진다") {
			shouldThrow<BusinessException> {
				UserIdealType.of(
					userId = 1L,
					ageMin = 30, ageMax = null,
					heightMin = null, heightMax = null,
					maritalStatus = null, smokingStatus = null,
					drinkingStatus = null, religion = null, distance = null,
				)
			}.errorCode shouldBe UserErrorCode.INVALID_IDEAL_TYPE_RANGE
		}

		it("나이가 허용 경계(20~60) 밖이면 INVALID_IDEAL_TYPE_RANGE를 던진다") {
			shouldThrow<BusinessException> {
				UserIdealType.of(
					userId = 1L,
					ageMin = 10, ageMax = 30,
					heightMin = null, heightMax = null,
					maritalStatus = null, smokingStatus = null,
					drinkingStatus = null, religion = null, distance = null,
				)
			}.errorCode shouldBe UserErrorCode.INVALID_IDEAL_TYPE_RANGE
		}

		it("키 최소가 최대보다 크면 INVALID_IDEAL_TYPE_RANGE를 던진다") {
			shouldThrow<BusinessException> {
				UserIdealType.of(
					userId = 1L,
					ageMin = null, ageMax = null,
					heightMin = 180, heightMax = 160,
					maritalStatus = null, smokingStatus = null,
					drinkingStatus = null, religion = null, distance = null,
				)
			}.errorCode shouldBe UserErrorCode.INVALID_IDEAL_TYPE_RANGE
		}
	}

	describe("update") {
		it("id와 userId를 보존하며 값을 교체한다") {
			val existing: UserIdealType = UserIdealType(
				id = 99L, userId = 1L,
				ageMin = 20, ageMax = 30,
			)

			val updated: UserIdealType = existing.update(
				ageMin = 27, ageMax = 35,
				heightMin = null, heightMax = null,
				maritalStatus = MaritalStatus.SINGLE, smokingStatus = null,
				drinkingStatus = null, religion = null, distance = DistancePreference.ADJACENT_REGION,
			)

			updated.id shouldBe 99L
			updated.userId shouldBe 1L
			updated.ageMin shouldBe 27
			updated.distance shouldBe DistancePreference.ADJACENT_REGION
		}

		it("교체 값이 잘못된 범위면 INVALID_IDEAL_TYPE_RANGE를 던진다") {
			val existing: UserIdealType = UserIdealType(id = 99L, userId = 1L)

			shouldThrow<BusinessException> {
				existing.update(
					ageMin = 50, ageMax = 40,
					heightMin = null, heightMax = null,
					maritalStatus = null, smokingStatus = null,
					drinkingStatus = null, religion = null, distance = null,
				)
			}.errorCode shouldBe UserErrorCode.INVALID_IDEAL_TYPE_RANGE
		}
	}
})
```

- [ ] **Step 4: 테스트 실행 → 컴파일 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.user.UserIdealTypeTest"`
Expected: FAIL — `UserIdealType` 미정의로 컴파일 에러.

- [ ] **Step 5: 도메인 모델 구현**

`oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/domain/UserIdealType.kt`:

```kotlin
package com.org.oneulsogae.core.user.command.domain

import com.org.oneulsogae.common.user.DistancePreference
import com.org.oneulsogae.common.user.DrinkingStatus
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.Religion
import com.org.oneulsogae.common.user.SmokingStatus
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode

/**
 * 사용자 이상형(매칭 선호) 도메인 모델. [User]와 1:1이며, 매칭 후보에 바로 비교 가능한 이산 값으로 보관한다.
 * - 나이/키: 숫자 경계(min/max). 둘 다 null이면 "상관없음".
 * - 결혼/흡연/음주/종교/거리: enum. null이면 "상관없음"(해당 조건 미적용).
 * 매칭 반영은 이번 범위 밖이며, 저장/조회만 담당한다. 영속성은 [com.org.oneulsogae.infra.user.command.entity.UserIdealTypeEntity]가 담당한다.
 */
data class UserIdealType(
	val id: Long = 0,
	val userId: Long,
	val ageMin: Int? = null,
	val ageMax: Int? = null,
	val heightMin: Int? = null,
	val heightMax: Int? = null,
	val maritalStatus: MaritalStatus? = null,
	val smokingStatus: SmokingStatus? = null,
	val drinkingStatus: DrinkingStatus? = null,
	val religion: Religion? = null,
	val distance: DistancePreference? = null,
) {

	init {
		validateIdealType()
	}

	/**
	 * 나이/키 범위 규칙을 검증한다. (생성·교체 모두 생성자 init에서 호출되므로 항상 유효 상태만 존재한다)
	 * - 범위는 최소·최대가 함께 존재하거나 함께 null이어야 한다. (한쪽만 입력 불가)
	 * - 최소 ≤ 최대이고, 허용 경계(나이 20~60, 키 150~195) 안이어야 한다.
	 */
	private fun validateIdealType() {
		validateRange(ageMin, ageMax, MIN_AGE, MAX_AGE)
		validateRange(heightMin, heightMax, MIN_HEIGHT, MAX_HEIGHT)
	}

	private fun validateRange(min: Int?, max: Int?, lower: Int, upper: Int) {
		if ((min == null) != (max == null)) {
			throw BusinessException(UserErrorCode.INVALID_IDEAL_TYPE_RANGE)
		}
		if (min != null && max != null) {
			if (min > max || min < lower || max > upper) {
				throw BusinessException(UserErrorCode.INVALID_IDEAL_TYPE_RANGE)
			}
		}
	}

	/** upsert 갱신 경로. id/userId를 보존하고 선호 값을 교체한다. (copy가 init 검증을 다시 태운다) */
	fun update(
		ageMin: Int?,
		ageMax: Int?,
		heightMin: Int?,
		heightMax: Int?,
		maritalStatus: MaritalStatus?,
		smokingStatus: SmokingStatus?,
		drinkingStatus: DrinkingStatus?,
		religion: Religion?,
		distance: DistancePreference?,
	): UserIdealType =
		copy(
			ageMin = ageMin,
			ageMax = ageMax,
			heightMin = heightMin,
			heightMax = heightMax,
			maritalStatus = maritalStatus,
			smokingStatus = smokingStatus,
			drinkingStatus = drinkingStatus,
			religion = religion,
			distance = distance,
		)

	companion object {

		private const val MIN_AGE: Int = 20
		private const val MAX_AGE: Int = 60
		private const val MIN_HEIGHT: Int = 150
		private const val MAX_HEIGHT: Int = 195

		/** 신규 이상형을 생성한다. (init 검증 통과분만 반환) */
		fun of(
			userId: Long,
			ageMin: Int?,
			ageMax: Int?,
			heightMin: Int?,
			heightMax: Int?,
			maritalStatus: MaritalStatus?,
			smokingStatus: SmokingStatus?,
			drinkingStatus: DrinkingStatus?,
			religion: Religion?,
			distance: DistancePreference?,
		): UserIdealType =
			UserIdealType(
				userId = userId,
				ageMin = ageMin,
				ageMax = ageMax,
				heightMin = heightMin,
				heightMax = heightMax,
				maritalStatus = maritalStatus,
				smokingStatus = smokingStatus,
				drinkingStatus = drinkingStatus,
				religion = religion,
				distance = distance,
			)
	}
}
```

- [ ] **Step 6: 테스트 실행 → 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.user.UserIdealTypeTest"`
Expected: PASS (8케이스).

- [ ] **Step 7: 커밋**

```bash
git add oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/user/DistancePreference.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/domain/UserIdealType.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/UserErrorCode.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/user/UserIdealTypeTest.kt
git commit -m "feat(user): 이상형 도메인 모델·거리 선호 enum 추가"
```

---

## Task 2: core command/query 배선 (포트·유스케이스·서비스)

**Files:**
- Create: `oneulsogae-core/.../user/command/application/port/in/command/SaveIdealTypeCommand.kt`
- Create: `oneulsogae-core/.../user/command/application/port/in/SaveIdealTypeUseCase.kt`
- Create: `oneulsogae-core/.../user/command/application/port/out/GetIdealTypePort.kt`
- Create: `oneulsogae-core/.../user/command/application/port/out/SaveIdealTypePort.kt`
- Create: `oneulsogae-core/.../user/command/application/SaveIdealTypeService.kt`
- Create: `oneulsogae-core/.../user/query/dto/IdealTypeView.kt`
- Create: `oneulsogae-core/.../user/query/dao/GetIdealTypeDao.kt`
- Create: `oneulsogae-core/.../user/query/service/port/in/GetIdealTypeUseCase.kt`
- Create: `oneulsogae-core/.../user/query/service/GetIdealTypeService.kt`

**Interfaces:**
- Consumes: `UserIdealType`, `UserIdealType.of/update` (Task 1)
- Produces:
  - `SaveIdealTypeCommand(ageMin, ageMax, heightMin, heightMax, maritalStatus, smokingStatus, drinkingStatus, religion, distance)` (모두 nullable)
  - `SaveIdealTypeUseCase.save(userId: Long, command: SaveIdealTypeCommand): UserIdealType`
  - `GetIdealTypePort.findByUserId(userId: Long): UserIdealType?`
  - `SaveIdealTypePort.save(idealType: UserIdealType): UserIdealType`
  - `IdealTypeView(ageMin, ageMax, heightMin, heightMax, maritalStatus, smokingStatus, drinkingStatus, religion, distance)` (모두 nullable, enum/Int)
  - `GetIdealTypeDao.findByUserId(userId: Long): IdealTypeView?`
  - `GetIdealTypeUseCase.findByUserId(userId: Long): IdealTypeView?`

이 태스크는 순수 배선(인터페이스+서비스)이라 자체 유닛 테스트를 두지 않는다(도메인은 Task 1, 동작은 Task 4 E2E가 검증 — CLAUDE.md 테스트 전략: 도메인→유닛, api→E2E). 검증은 core 컴파일로 한다.

- [ ] **Step 1: command DTO 작성**

`.../command/application/port/in/command/SaveIdealTypeCommand.kt`:

```kotlin
package com.org.oneulsogae.core.user.command.application.port.`in`.command

import com.org.oneulsogae.common.user.DistancePreference
import com.org.oneulsogae.common.user.DrinkingStatus
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.Religion
import com.org.oneulsogae.common.user.SmokingStatus

/** 이상형 저장(upsert) 명령. 모든 항목은 선택이며 null = "상관없음". 나이/키는 min/max로 분해돼 전달된다. */
data class SaveIdealTypeCommand(
	val ageMin: Int?,
	val ageMax: Int?,
	val heightMin: Int?,
	val heightMax: Int?,
	val maritalStatus: MaritalStatus?,
	val smokingStatus: SmokingStatus?,
	val drinkingStatus: DrinkingStatus?,
	val religion: Religion?,
	val distance: DistancePreference?,
)
```

- [ ] **Step 2: 저장 유스케이스(in-port) 작성**

`.../command/application/port/in/SaveIdealTypeUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.user.command.application.port.`in`

import com.org.oneulsogae.core.user.command.application.port.`in`.command.SaveIdealTypeCommand
import com.org.oneulsogae.core.user.command.domain.UserIdealType

/** 현재 사용자의 이상형을 저장(신규 생성 또는 교체)하는 인포트. */
interface SaveIdealTypeUseCase {

	fun save(userId: Long, command: SaveIdealTypeCommand): UserIdealType
}
```

- [ ] **Step 3: out-port 2개 작성**

`.../command/application/port/out/GetIdealTypePort.kt`:

```kotlin
package com.org.oneulsogae.core.user.command.application.port.out

import com.org.oneulsogae.core.user.command.domain.UserIdealType

/** 이상형 단건 로드 아웃포트(upsert 시 기존 행 조회용). 조회 read model은 query 쪽 [com.org.oneulsogae.core.user.query.dao.GetIdealTypeDao]가 따로 둔다. */
interface GetIdealTypePort {

	fun findByUserId(userId: Long): UserIdealType?
}
```

`.../command/application/port/out/SaveIdealTypePort.kt`:

```kotlin
package com.org.oneulsogae.core.user.command.application.port.out

import com.org.oneulsogae.core.user.command.domain.UserIdealType

/** 이상형 저장 아웃포트. 신규 저장 또는 기존 행(id 존재) 갱신을 반영한다. */
interface SaveIdealTypePort {

	fun save(idealType: UserIdealType): UserIdealType
}
```

- [ ] **Step 4: 저장 서비스 작성**

`.../command/application/SaveIdealTypeService.kt`:

```kotlin
package com.org.oneulsogae.core.user.command.application

import com.org.oneulsogae.core.user.command.application.port.`in`.SaveIdealTypeUseCase
import com.org.oneulsogae.core.user.command.application.port.`in`.command.SaveIdealTypeCommand
import com.org.oneulsogae.core.user.command.application.port.out.GetIdealTypePort
import com.org.oneulsogae.core.user.command.application.port.out.SaveIdealTypePort
import com.org.oneulsogae.core.user.command.domain.UserIdealType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [SaveIdealTypeUseCase] 구현. 기존 이상형이 있으면 교체(update), 없으면 새로 만들어(of) 저장한다(upsert).
 * 매칭 읽기 모델과 무관하므로 프로필과 달리 도메인 이벤트를 발행하지 않는다.
 */
@Service
class SaveIdealTypeService(
	private val getIdealTypePort: GetIdealTypePort,
	private val saveIdealTypePort: SaveIdealTypePort,
) : SaveIdealTypeUseCase {

	@Transactional
	override fun save(userId: Long, command: SaveIdealTypeCommand): UserIdealType {
		val existing: UserIdealType? = getIdealTypePort.findByUserId(userId)
		val idealType: UserIdealType = existing?.update(
			ageMin = command.ageMin,
			ageMax = command.ageMax,
			heightMin = command.heightMin,
			heightMax = command.heightMax,
			maritalStatus = command.maritalStatus,
			smokingStatus = command.smokingStatus,
			drinkingStatus = command.drinkingStatus,
			religion = command.religion,
			distance = command.distance,
		) ?: UserIdealType.of(
			userId = userId,
			ageMin = command.ageMin,
			ageMax = command.ageMax,
			heightMin = command.heightMin,
			heightMax = command.heightMax,
			maritalStatus = command.maritalStatus,
			smokingStatus = command.smokingStatus,
			drinkingStatus = command.drinkingStatus,
			religion = command.religion,
			distance = command.distance,
		)
		return saveIdealTypePort.save(idealType)
	}
}
```

- [ ] **Step 5: query read model(view) 작성**

`.../query/dto/IdealTypeView.kt`:

```kotlin
package com.org.oneulsogae.core.user.query.dto

import com.org.oneulsogae.common.user.DistancePreference
import com.org.oneulsogae.common.user.DrinkingStatus
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.Religion
import com.org.oneulsogae.common.user.SmokingStatus

/** 이상형 조회 read model. null = "상관없음". */
data class IdealTypeView(
	val ageMin: Int?,
	val ageMax: Int?,
	val heightMin: Int?,
	val heightMax: Int?,
	val maritalStatus: MaritalStatus?,
	val smokingStatus: SmokingStatus?,
	val drinkingStatus: DrinkingStatus?,
	val religion: Religion?,
	val distance: DistancePreference?,
)
```

- [ ] **Step 6: query dao(포트) + 유스케이스(in-port) 작성**

`.../query/dao/GetIdealTypeDao.kt`:

```kotlin
package com.org.oneulsogae.core.user.query.dao

import com.org.oneulsogae.core.user.query.dto.IdealTypeView

/** 이상형 조회 dao(query out-port). read model([IdealTypeView])을 반환한다. 없으면 null. */
interface GetIdealTypeDao {

	fun findByUserId(userId: Long): IdealTypeView?
}
```

`.../query/service/port/in/GetIdealTypeUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.user.query.service.port.`in`

import com.org.oneulsogae.core.user.query.dto.IdealTypeView

/** userId로 이상형을 조회하는 인포트. 미설정 사용자도 진입 가능해야 하므로 없으면 null(예외 아님). */
interface GetIdealTypeUseCase {

	fun findByUserId(userId: Long): IdealTypeView?
}
```

- [ ] **Step 7: 조회 서비스 작성**

`.../query/service/GetIdealTypeService.kt`:

```kotlin
package com.org.oneulsogae.core.user.query.service

import com.org.oneulsogae.core.user.query.dao.GetIdealTypeDao
import com.org.oneulsogae.core.user.query.dto.IdealTypeView
import com.org.oneulsogae.core.user.query.service.port.`in`.GetIdealTypeUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** [GetIdealTypeUseCase] 구현. 조회 dao([GetIdealTypeDao])에만 의존한다. */
@Service
class GetIdealTypeService(
	private val getIdealTypeDao: GetIdealTypeDao,
) : GetIdealTypeUseCase {

	@Transactional(readOnly = true)
	override fun findByUserId(userId: Long): IdealTypeView? =
		getIdealTypeDao.findByUserId(userId)
}
```

- [ ] **Step 8: core 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/query
git commit -m "feat(user): 이상형 저장·조회 유스케이스·포트 추가"
```

---

## Task 3: infra 영속성 (엔티티·매퍼·리포지토리·어댑터·daoImpl)

**Files:**
- Create: `oneulsogae-infra/.../user/command/entity/UserIdealTypeEntity.kt`
- Create: `oneulsogae-infra/.../user/command/mapper/UserIdealTypeMapper.kt`
- Create: `oneulsogae-infra/.../user/command/repository/UserIdealTypeJpaRepository.kt`
- Create: `oneulsogae-infra/.../user/command/adapter/UserIdealTypeCoreAdapter.kt`
- Create: `oneulsogae-infra/.../user/query/GetIdealTypeDaoImpl.kt`

**Interfaces:**
- Consumes: `GetIdealTypePort`, `SaveIdealTypePort`, `GetIdealTypeDao`, `UserIdealType`, `IdealTypeView` (Task 2)
- Produces:
  - `UserIdealTypeEntity(userId: Long, ageMin, ageMax, heightMin, heightMax, maritalStatus, smokingStatus, drinkingStatus, religion, distance)` — `@Table(name="user_ideal_types")`, `var` 컬럼
  - `UserIdealTypeEntity.toDomain()`, `UserIdealType.toEntity()`
  - `UserIdealTypeJpaRepository.findByUserId(userId: Long): UserIdealTypeEntity?`

- [ ] **Step 1: 엔티티 작성**

`.../user/command/entity/UserIdealTypeEntity.kt`:

```kotlin
package com.org.oneulsogae.infra.user.command.entity

import com.org.oneulsogae.common.user.DistancePreference
import com.org.oneulsogae.common.user.DrinkingStatus
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.Religion
import com.org.oneulsogae.common.user.SmokingStatus
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * user_ideal_types 테이블 영속성 엔티티. 사용자(users)와 1:1로 연결되는 이상형(매칭 선호)을 보관한다.
 * 나이/키는 숫자 경계로, 나머지는 enum으로 저장한다(모두 nullable, null = "상관없음"). 도메인 로직은 두지 않는다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "user_ideal_types",
	indexes = [
		Index(name = "ux_user_ideal_type_user_id", columnList = "user_id", unique = true),
	],
)
class UserIdealTypeEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** 선호 나이 하한. */
	@Column(name = "age_min")
	var ageMin: Int? = null,

	/** 선호 나이 상한. */
	@Column(name = "age_max")
	var ageMax: Int? = null,

	/** 선호 키(cm) 하한. */
	@Column(name = "height_min")
	var heightMin: Int? = null,

	/** 선호 키(cm) 상한. */
	@Column(name = "height_max")
	var heightMax: Int? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "marital_status", columnDefinition = "varchar(50)")
	var maritalStatus: MaritalStatus? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "smoking_status", columnDefinition = "varchar(50)")
	var smokingStatus: SmokingStatus? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "drinking_status", columnDefinition = "varchar(50)")
	var drinkingStatus: DrinkingStatus? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "religion", columnDefinition = "varchar(50)")
	var religion: Religion? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "distance", columnDefinition = "varchar(50)")
	var distance: DistancePreference? = null,
) : BaseEntity()
```

- [ ] **Step 2: 매퍼 작성**

`.../user/command/mapper/UserIdealTypeMapper.kt`:

```kotlin
package com.org.oneulsogae.infra.user.command.mapper

import com.org.oneulsogae.core.user.command.domain.UserIdealType
import com.org.oneulsogae.infra.user.command.entity.UserIdealTypeEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun UserIdealTypeEntity.toDomain(): UserIdealType =
	UserIdealType(
		id = id ?: 0,
		userId = userId,
		ageMin = ageMin,
		ageMax = ageMax,
		heightMin = heightMin,
		heightMax = heightMax,
		maritalStatus = maritalStatus,
		smokingStatus = smokingStatus,
		drinkingStatus = drinkingStatus,
		religion = religion,
		distance = distance,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규 저장(INSERT), 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun UserIdealType.toEntity(): UserIdealTypeEntity =
	UserIdealTypeEntity(
		userId = userId,
		ageMin = ageMin,
		ageMax = ageMax,
		heightMin = heightMin,
		heightMax = heightMax,
		maritalStatus = maritalStatus,
		smokingStatus = smokingStatus,
		drinkingStatus = drinkingStatus,
		religion = religion,
		distance = distance,
	).also { if (id != 0L) it.id = id }
```

- [ ] **Step 3: 리포지토리 작성**

`.../user/command/repository/UserIdealTypeJpaRepository.kt`:

```kotlin
package com.org.oneulsogae.infra.user.command.repository

import com.org.oneulsogae.infra.user.command.entity.UserIdealTypeEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 사용자 이상형 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 [com.org.oneulsogae.infra.user.command.adapter.UserIdealTypeCoreAdapter]가 구현하고,
 * 조회 read model 투영은 [com.org.oneulsogae.infra.user.query.GetIdealTypeDaoImpl]가 이 리포지토리를 재사용한다.
 */
interface UserIdealTypeJpaRepository : JpaRepository<UserIdealTypeEntity, Long> {

	fun findByUserId(userId: Long): UserIdealTypeEntity?
}
```

- [ ] **Step 4: 어댑터 작성 (command out-port 구현)**

`.../user/command/adapter/UserIdealTypeCoreAdapter.kt`:

```kotlin
package com.org.oneulsogae.infra.user.command.adapter

import com.org.oneulsogae.core.user.command.application.port.out.GetIdealTypePort
import com.org.oneulsogae.core.user.command.application.port.out.SaveIdealTypePort
import com.org.oneulsogae.core.user.command.domain.UserIdealType
import com.org.oneulsogae.infra.user.command.mapper.toDomain
import com.org.oneulsogae.infra.user.command.mapper.toEntity
import com.org.oneulsogae.infra.user.command.repository.UserIdealTypeJpaRepository
import org.springframework.stereotype.Component

/**
 * [com.org.oneulsogae.infra.user.command.entity.UserIdealTypeEntity]의 command out-port 어댑터. (Spring Data 메서드 쿼리)
 * upsert 시 서비스가 [findByUserId]로 기존 행을 로드해 id를 보존하므로, 저장은 id 유무에 따라 INSERT/UPDATE로 갈린다.
 */
@Component
class UserIdealTypeCoreAdapter(
	private val userIdealTypeJpaRepository: UserIdealTypeJpaRepository,
) : GetIdealTypePort, SaveIdealTypePort {

	override fun findByUserId(userId: Long): UserIdealType? =
		userIdealTypeJpaRepository.findByUserId(userId)?.toDomain()

	override fun save(idealType: UserIdealType): UserIdealType =
		userIdealTypeJpaRepository.save(idealType.toEntity()).toDomain()
}
```

- [ ] **Step 5: 조회 daoImpl 작성 (command 리포지토리 재사용)**

`.../user/query/GetIdealTypeDaoImpl.kt`:

```kotlin
package com.org.oneulsogae.infra.user.query

import com.org.oneulsogae.core.user.query.dao.GetIdealTypeDao
import com.org.oneulsogae.core.user.query.dto.IdealTypeView
import com.org.oneulsogae.infra.user.command.repository.UserIdealTypeJpaRepository
import org.springframework.stereotype.Component

/**
 * [GetIdealTypeDao]의 구현체. 조인이 없어 QueryDSL 대신 command 리포지토리를 재사용해 read model로 투영한다.
 * (infra 내부 query→command 참조 허용 규칙에 따른다)
 */
@Component
class GetIdealTypeDaoImpl(
	private val userIdealTypeJpaRepository: UserIdealTypeJpaRepository,
) : GetIdealTypeDao {

	override fun findByUserId(userId: Long): IdealTypeView? =
		userIdealTypeJpaRepository.findByUserId(userId)?.let {
			IdealTypeView(
				ageMin = it.ageMin,
				ageMax = it.ageMax,
				heightMin = it.heightMin,
				heightMax = it.heightMax,
				maritalStatus = it.maritalStatus,
				smokingStatus = it.smokingStatus,
				drinkingStatus = it.drinkingStatus,
				religion = it.religion,
				distance = it.distance,
			)
		}
}
```

- [ ] **Step 6: infra 컴파일 확인**

Run: `./gradlew :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL (QUserIdealTypeEntity 메타모델도 생성됨 — E2E cleanup에서 사용).

- [ ] **Step 7: 커밋**

```bash
git add oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/entity/UserIdealTypeEntity.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/mapper/UserIdealTypeMapper.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/repository/UserIdealTypeJpaRepository.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/adapter/UserIdealTypeCoreAdapter.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/query/GetIdealTypeDaoImpl.kt
git commit -m "feat(user): 이상형 영속성 어댑터·엔티티 추가"
```

---

## Task 4: API 계층 + E2E (요청/응답/컨트롤러 + 왕복 검증)

**Files:**
- Create: `oneulsogae-api/.../api/user/request/SaveIdealTypeRequest.kt`
- Create: `oneulsogae-api/.../api/user/response/IdealTypeResponse.kt`
- Create: `oneulsogae-api/.../api/user/IdealTypeController.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/user/IdealTypeE2ETest.kt`

**Interfaces:**
- Consumes: `SaveIdealTypeUseCase`, `GetIdealTypeUseCase`, `SaveIdealTypeCommand`, `IdealTypeView`, `UserIdealType` (Task 2)
- Produces:
  - `SaveIdealTypeRequest(ageRange: List<Int>?, heightRange: List<Int>?, maritalStatus, smoking, drinking, religion, distance)` + `toCommand(): SaveIdealTypeCommand`
  - `IdealTypeResponse.of(view)`, `.of(domain)`, `.empty()`
  - `IdealTypeController` — `GET/PUT /users/v1/ideal-type`

- [ ] **Step 1: 요청 DTO 작성**

`.../api/user/request/SaveIdealTypeRequest.kt`:

```kotlin
package com.org.oneulsogae.api.user.request

import com.org.oneulsogae.common.user.DistancePreference
import com.org.oneulsogae.common.user.DrinkingStatus
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.Religion
import com.org.oneulsogae.common.user.SmokingStatus
import com.org.oneulsogae.core.user.command.application.port.`in`.command.SaveIdealTypeCommand
import jakarta.validation.constraints.Size

/**
 * 이상형 저장 요청(PUT, 전체 교체). 모든 항목은 선택이며 생략(null)은 "상관없음"을 뜻한다.
 * enum은 백엔드 enum name으로 받는다. 나이/키 범위는 `[최소, 최대]` 2요소 배열로 받아 명령에서 min/max로 분해한다.
 * 값 규칙(min ≤ max·경계·짝 존재)은 도메인 [com.org.oneulsogae.core.user.command.domain.UserIdealType]가 검증한다.
 */
data class SaveIdealTypeRequest(
	@field:Size(min = 2, max = 2, message = "나이 범위는 [최소, 최대] 두 값이어야 합니다.")
	val ageRange: List<Int>? = null,

	@field:Size(min = 2, max = 2, message = "키 범위는 [최소, 최대] 두 값이어야 합니다.")
	val heightRange: List<Int>? = null,

	val maritalStatus: MaritalStatus? = null,

	val smoking: SmokingStatus? = null,

	val drinking: DrinkingStatus? = null,

	val religion: Religion? = null,

	val distance: DistancePreference? = null,
) {

	fun toCommand(): SaveIdealTypeCommand =
		SaveIdealTypeCommand(
			ageMin = ageRange?.get(0),
			ageMax = ageRange?.get(1),
			heightMin = heightRange?.get(0),
			heightMax = heightRange?.get(1),
			maritalStatus = maritalStatus,
			smokingStatus = smoking,
			drinkingStatus = drinking,
			religion = religion,
			distance = distance,
		)
}
```

- [ ] **Step 2: 응답 DTO 작성**

`.../api/user/response/IdealTypeResponse.kt`:

```kotlin
package com.org.oneulsogae.api.user.response

import com.org.oneulsogae.common.user.DistancePreference
import com.org.oneulsogae.common.user.DrinkingStatus
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.Religion
import com.org.oneulsogae.common.user.SmokingStatus
import com.org.oneulsogae.core.user.command.domain.UserIdealType
import com.org.oneulsogae.core.user.query.dto.IdealTypeView

/**
 * 이상형 응답. 프론트 `IdealType`와 필드·형태를 맞춘다(배열형 ageRange/heightRange, enum name).
 * null = "상관없음". 나이/키는 min·max가 모두 있을 때만 배열로, 하나라도 없으면 null로 내려간다.
 */
data class IdealTypeResponse(
	val ageRange: List<Int>?,
	val heightRange: List<Int>?,
	val maritalStatus: MaritalStatus?,
	val smoking: SmokingStatus?,
	val drinking: DrinkingStatus?,
	val religion: Religion?,
	val distance: DistancePreference?,
) {
	companion object {

		/** 조회(query) read model 매핑. */
		fun of(view: IdealTypeView): IdealTypeResponse =
			IdealTypeResponse(
				ageRange = range(view.ageMin, view.ageMax),
				heightRange = range(view.heightMin, view.heightMax),
				maritalStatus = view.maritalStatus,
				smoking = view.smokingStatus,
				drinking = view.drinkingStatus,
				religion = view.religion,
				distance = view.distance,
			)

		/** 명령(command) 결과 도메인 매핑. (저장 후 갱신된 [UserIdealType] 렌더링) */
		fun of(domain: UserIdealType): IdealTypeResponse =
			IdealTypeResponse(
				ageRange = range(domain.ageMin, domain.ageMax),
				heightRange = range(domain.heightMin, domain.heightMax),
				maritalStatus = domain.maritalStatus,
				smoking = domain.smokingStatus,
				drinking = domain.drinkingStatus,
				religion = domain.religion,
				distance = domain.distance,
			)

		/** 미설정 사용자 응답. 전 항목 null("상관없음"). */
		fun empty(): IdealTypeResponse =
			IdealTypeResponse(null, null, null, null, null, null, null)

		private fun range(min: Int?, max: Int?): List<Int>? =
			if (min != null && max != null) listOf(min, max) else null
	}
}
```

- [ ] **Step 3: 컨트롤러 작성**

`.../api/user/IdealTypeController.kt`:

```kotlin
package com.org.oneulsogae.api.user

import com.org.oneulsogae.api.user.request.SaveIdealTypeRequest
import com.org.oneulsogae.api.user.response.IdealTypeResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.user.command.application.port.`in`.SaveIdealTypeUseCase
import com.org.oneulsogae.core.user.query.service.port.`in`.GetIdealTypeUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users/v1/ideal-type")
@Tag(name = "이상형 설정", description = "로그인 사용자의 이상형(매칭 선호) 조회 및 저장 엔드포인트")
class IdealTypeController(
	private val getIdealTypeUseCase: GetIdealTypeUseCase,
	private val saveIdealTypeUseCase: SaveIdealTypeUseCase,
) {

	/** 현재 로그인 사용자의 이상형을 조회한다. 미설정이면 전 항목 null("상관없음")로 내려준다. */
	@Operation(summary = "내 이상형 조회", description = "현재 로그인 사용자의 이상형을 조회한다. 미설정이면 전 항목이 null이다.")
	@GetMapping
	fun getMyIdealType(
		@LoginUser user: AuthUser,
	): ApiResponse<IdealTypeResponse> =
		ApiResponse.success(
			getIdealTypeUseCase.findByUserId(user.id)?.let(IdealTypeResponse::of) ?: IdealTypeResponse.empty(),
		)

	/** 현재 로그인 사용자의 이상형을 저장한다(신규 생성 또는 전체 교체). */
	@Operation(summary = "내 이상형 저장", description = "현재 로그인 사용자의 이상형을 저장(upsert)한다. 생략(null)한 항목은 '상관없음'으로 저장된다.")
	@PutMapping
	fun saveMyIdealType(
		@LoginUser user: AuthUser,
		@Valid @RequestBody request: SaveIdealTypeRequest,
	): ApiResponse<IdealTypeResponse> =
		ApiResponse.success(IdealTypeResponse.of(saveIdealTypeUseCase.save(user.id, request.toCommand())))
}
```

- [ ] **Step 4: 실패하는 E2E 테스트 작성**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/user/IdealTypeE2ETest.kt`:

```kotlin
package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.integration.put
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.user.command.entity.QUserIdealTypeEntity
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.nullValue

/**
 * `GET/PUT /users/v1/ideal-type` E2E 테스트.
 * upsert 왕복, 미설정 응답(전 항목 null), enum name·배열 형태, 범위 검증, 인증을 검증한다.
 */
class IdealTypeE2ETest : AbstractIntegrationSupport({

	describe("GET /users/v1/ideal-type") {
		context("이상형을 설정한 적 없는 사용자가 조회하면") {
			it("전 항목이 null인 기본 응답을 내려준다 (200)") {
				val userId: Long = 7001L

				get("/users/v1/ideal-type") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data.ageRange", nullValue())
					body("data.maritalStatus", nullValue())
					body("data.distance", nullValue())
				}
			}
		}

		context("인증 없이 조회하면") {
			it("401을 반환한다") {
				get("/users/v1/ideal-type") expect {
					status(401)
				}
			}
		}
	}

	describe("PUT /users/v1/ideal-type") {
		context("유효한 이상형을 저장하면") {
			it("저장 후 조회 시 저장한 값이 그대로 내려온다 (enum name·배열)") {
				val userId: Long = 7002L

				put("/users/v1/ideal-type") {
					bearer(accessTokenFor(userId))
					jsonBody(
						"""
						{
						  "ageRange": [27, 35],
						  "heightRange": null,
						  "maritalStatus": "SINGLE",
						  "smoking": "NON_SMOKER",
						  "drinking": "SOMETIMES",
						  "religion": null,
						  "distance": "SAME_REGION"
						}
						""".trimIndent(),
					)
				} expect {
					status(200)
					body("success", true)
					body("data.ageRange", contains(27, 35))
					body("data.maritalStatus", "SINGLE")
					body("data.distance", "SAME_REGION")
				}

				get("/users/v1/ideal-type") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.ageRange", contains(27, 35))
					body("data.heightRange", nullValue())
					body("data.smoking", "NON_SMOKER")
					body("data.drinking", "SOMETIMES")
					body("data.religion", nullValue())
					body("data.distance", "SAME_REGION")
				}
			}
		}

		context("이미 이상형이 있는 사용자가 다시 저장하면") {
			it("새 값으로 교체(upsert)되고 행이 중복 생성되지 않는다") {
				val userId: Long = 7003L

				put("/users/v1/ideal-type") {
					bearer(accessTokenFor(userId))
					jsonBody("""{ "ageRange": [20, 30], "distance": "SAME_REGION" }""")
				} expect {
					status(200)
				}

				put("/users/v1/ideal-type") {
					bearer(accessTokenFor(userId))
					jsonBody("""{ "ageRange": [40, 50], "distance": "ADJACENT_REGION" }""")
				} expect {
					status(200)
					body("data.ageRange", contains(40, 50))
					body("data.distance", "ADJACENT_REGION")
				}

				get("/users/v1/ideal-type") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.ageRange", contains(40, 50))
					body("data.distance", "ADJACENT_REGION")
				}
			}
		}

		context("최소가 최대보다 큰 나이 범위를 저장하면") {
			it("검증 실패로 400을 반환한다") {
				val userId: Long = 7004L

				put("/users/v1/ideal-type") {
					bearer(accessTokenFor(userId))
					jsonBody("""{ "ageRange": [40, 20] }""")
				} expect {
					status(400)
					body("success", false)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QUserIdealTypeEntity.userIdealTypeEntity)
	}
})
```

- [ ] **Step 5: E2E 실행 → 컴파일/동작 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.user.IdealTypeE2ETest"`
Expected: FAIL — 컨트롤러/DTO 미배선 또는 엔드포인트 404. (Step 1~3을 이미 작성했다면 이 태스크는 GREEN이어야 하므로, 실패가 남으면 원인을 고친다.)

- [ ] **Step 6: E2E 실행 → 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.user.IdealTypeE2ETest"`
Expected: PASS (5케이스: 미설정 조회, 인증 없음 401, 저장·왕복, upsert 교체, 범위 400).

- [ ] **Step 7: 커밋**

```bash
git add oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/user/IdealTypeController.kt \
        oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/user/request/SaveIdealTypeRequest.kt \
        oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/user/response/IdealTypeResponse.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/user/IdealTypeE2ETest.kt
git commit -m "feat(user): 이상형 조회·저장 API 추가"
```

---

## Task 5: DB 마이그레이션 SQL

**Files:**
- Create: `docs/migration/user_ideal_types.sql`

`ddl-auto: update` 환경이라 앱이 테이블을 자동 생성하지만, 기존 관례대로 실DB 반영용 DDL을 명시 파일로 남긴다.

- [ ] **Step 1: 마이그레이션 SQL 작성**

`docs/migration/user_ideal_types.sql`:

```sql
-- 이상형(매칭 선호) 저장용 테이블. 사용자(users)와 1:1이며, user_id에 유니크 인덱스를 둔다.
-- 나이/키는 숫자 경계, 나머지는 enum 문자열로 저장한다(모두 NULL 허용, NULL = "상관없음").
CREATE TABLE user_ideal_types (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    age_min         INT         NULL,
    age_max         INT         NULL,
    height_min      INT         NULL,
    height_max      INT         NULL,
    marital_status  VARCHAR(50) NULL,
    smoking_status  VARCHAR(50) NULL,
    drinking_status VARCHAR(50) NULL,
    religion        VARCHAR(50) NULL,
    distance        VARCHAR(50) NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    deleted_at      DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_user_ideal_type_user_id (user_id)
);
```

- [ ] **Step 2: 커밋**

```bash
git add docs/migration/user_ideal_types.sql
git commit -m "build: user_ideal_types 테이블 마이그레이션 SQL 추가"
```

---

## Task 6: 전체 회귀 확인

- [ ] **Step 1: 관련 모듈 전체 테스트**

Run: `./gradlew :oneulsogae-api:test`
Expected: BUILD SUCCESSFUL (신규 유닛 + E2E 포함, 기존 테스트 무회귀).

- [ ] **Step 2: 전체 빌드**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

---

## Self-Review

**1. Spec coverage:**
- §2 계약(enum name·null=상관없음·배열형·필드명 유지) → Task 1(enum/도메인), Task 4(request/response 필드명 `smoking`/`drinking`, 배열형, enum name). ✅
- §3 매칭 효율 저장(숫자 경계 + enum) → Task 1 도메인, Task 3 엔티티 컬럼. ✅
- §4 데이터 모델(`DistancePreference`, `user_ideal_types` unique user_id) → Task 1, 3, 5. ✅
- §5 도메인 검증 캡슐화(`validateIdealType`/범위 규칙) → Task 1. ✅
- §6 헥사고날 구성(command/query 포트·서비스·컨트롤러·adapter·daoImpl) → Task 2, 3, 4. ✅
- §6.5 에러 코드 → Task 1(`INVALID_IDEAL_TYPE_RANGE`, USER-019). ✅
- §7 테스트(도메인 유닛 + E2E 왕복/upsert/미설정/범위/인증) → Task 1, 4. ✅
- §8 프론트 대응은 안내만(백엔드 수정 없음) → 코드 작업 없음, 스펙 문서에 기재. ✅ (해당 없음)

**2. Placeholder scan:** TBD/TODO/"적절히 처리" 없음. 모든 코드 스텝에 실제 코드 포함. ✅

**3. Type consistency:**
- `SaveIdealTypeUseCase.save(userId, command)` — Task 2 정의 = Task 4 호출. ✅
- `GetIdealTypeUseCase.findByUserId(userId): IdealTypeView?` — Task 2 정의 = Task 4 `?.let(IdealTypeResponse::of)` 사용. ✅
- `IdealTypeView` 필드(`smokingStatus`/`drinkingStatus`) ↔ 응답 필드(`smoking`/`drinking`) 매핑 — Task 4 `of(view)`에서 `smoking = view.smokingStatus`로 정확히 변환. ✅
- 도메인 `smokingStatus`/`drinkingStatus` ↔ command 동명 ↔ request `smoking`/`drinking` — Task 4 `toCommand()`에서 `smokingStatus = smoking`으로 변환. ✅
- `UserIdealType.of/update` 파라미터 순서 — Task 1 정의 = Task 2 서비스 호출(명명 인자 사용으로 안전). ✅
- 엔티티 컬럼명(`age_min` 등) ↔ 마이그레이션 SQL 컬럼명 일치. ✅

이상 없음.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-07-01-user-ideal-type.md`. 두 가지 실행 방식이 있습니다:**

**1. Subagent-Driven (recommended)** — 태스크마다 새 서브에이전트를 띄우고 태스크 사이에 리뷰, 빠른 반복.

**2. Inline Execution** — 이 세션에서 executing-plans로 체크포인트 리뷰와 함께 배치 실행.

**어느 방식으로 진행할까요?**
