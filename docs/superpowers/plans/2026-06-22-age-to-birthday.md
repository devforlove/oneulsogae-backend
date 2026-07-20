# age → birthday 전환 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 온보딩 입력·저장·내부 전파를 나이(`age: Int`)에서 생년월일(`birthday: LocalDate`)로 일괄 전환하고, 표시용 나이는 응답 경계에서만 birthday로부터 파생한다.

**Architecture:** `birthday`를 단일 진실원천으로 DB(`user_details.birthday`, `match_user.birthday`)·도메인·이벤트·읽기모델에 저장·전파한다. 매칭 로직은 나이를 비교하지 않으므로(표시용), 매칭 적격성은 birthday 존재 여부만 본다. API 응답 JSON 계약은 기존과 동일하게 `age: Int`를 유지하되, 응답 팩토리가 `TimeGenerator.today()` 기준으로 birthday에서 나이를 계산한다.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4 / Spring Data JPA + QueryDSL / MySQL(ddl-auto) / Kotest(유닛) / RestAssured + Testcontainers(E2E).

## Global Constraints

- **운영 데이터 없음**: `age` 컬럼은 제거하고 `birthday DATE`를 새로 만든다. 백필/마이그레이션 SQL은 남기지 않고 ddl-auto에 위임한다(레포에 마이그레이션 도구 없음).
- **현재 시각은 `TimeGenerator` 주입**: `LocalDateTime.now()`/`LocalDate.now()`를 애플리케이션·도메인에서 직접 호출하지 않는다. 나이 파생·검증의 기준일(`today`)은 `TimeGenerator.today()`로 얻어 파라미터로 넘긴다. (직접 호출은 엔티티/픽스처·테스트 단언에 한정)
- **타입/포맷**: 도메인·엔티티 `java.time.LocalDate`, DB `DATE`, JSON ISO-8601(`"1995-03-21"`).
- **나이 검증**: 만 19세 이상 100세 이하(기존 규칙 보존). 도메인 `UserDetail`에서 검증한다.
- **응답 계약 불변**: 모든 표시 응답 필드명은 `age: Int`(또는 `Int?`) 그대로 둔다. birthday를 클라이언트에 노출하지 않는다.
- **타입 명시**: 변수·반환 타입·람다 파라미터 타입을 생략하지 않는다(기존 컨벤션).
- **수술적 변경**: age→birthday와 무관한 코드/주석/포맷은 건드리지 않는다. 단 주석에 "나이"가 박혀 있고 의미가 birthday로 바뀌는 경우만 함께 정정한다.

이 전환은 한 필드를 여러 모듈에서 동시에 바꾸는 작업이라 **중간 상태는 전체 빌드가 통과하지 않는다.** 따라서 각 Task는 가능한 모듈 단위로 컴파일 가능 지점을 잡고(`:module:compileKotlin`), 마지막 Task에서 전체 `./gradlew build`로 검증한다. 모듈 의존: common ← core ← (scheduler) ← infra ← api.

---

### Task 1: 나이 파생 공용 함수 (`ageAt`)

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/common/time/BirthdayAge.kt`

**Interfaces:**
- Produces: `fun LocalDate.ageAt(today: LocalDate): Int` — 생년월일과 기준일로 만 나이를 계산. 검증(도메인)·표시(응답)에서 공통으로 쓴다.

- [ ] **Step 1: 확장 함수 작성**

```kotlin
package com.org.oneulsogae.core.common.time

import java.time.LocalDate
import java.time.Period

/** 생년월일([this])과 기준일([today])로 만 나이를 계산한다. 나이 검증(도메인)과 표시(응답)에서 공통으로 쓴다. */
fun LocalDate.ageAt(today: LocalDate): Int = Period.between(this, today).years
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/common/time/BirthdayAge.kt
git commit -m "feat: 생년월일→만나이 파생 확장 함수 ageAt 추가"
```

---

### Task 2: core 도메인·포트·서비스·이벤트·읽기모델 birthday 전환

core 모듈의 모든 `age` 참조를 birthday로 바꾼다. core는 common에만 의존하므로 이 Task 완료 시 `:oneulsogae-core:build`가 통과해야 한다(테스트 픽스처 포함).

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/UserErrorCode.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/domain/UserDetail.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application/port/in/command/UpdateUserDetailCommand.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application/UpdateUserDetailService.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/common/event/MatchProfileSnapshot.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/MatchUser.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/query/dto/UserDetailView.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/query/dto/InvitableUser.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/query/dto/ReceivedInvitation.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/query/dto/SentInvitation.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/query/dto/MatchWithPartner.kt`
- Modify: `oneulsogae-core/src/testFixtures/kotlin/com/org/oneulsogae/core/fixture/UserDetailFixture.kt`

**Interfaces:**
- Consumes: `LocalDate.ageAt(today)` (Task 1).
- Produces:
  - `UserDetail.birthday: LocalDate?`, `UserDetail.age(today: LocalDate): Int?`, `UserDetail.initProfile(..., birthday: LocalDate?, ..., today: LocalDate)`.
  - `UpdateUserDetailCommand.birthday: LocalDate`.
  - `MatchProfileSnapshot.birthday: LocalDate`, `MatchUser.birthday: LocalDate`.
  - 읽기모델 필드 `birthday`(`UserDetailView.birthday: LocalDate?`, `InvitableUser.birthday: LocalDate`, `ReceivedInvitationInviter.birthday: LocalDate`, `SentInvitationMember.birthday: LocalDate`, `MatchWithPartner.birthday: LocalDate?`).
  - `UserErrorCode.BIRTHDAY_REQUIRED`, `UserErrorCode.INVALID_BIRTHDAY`.

- [ ] **Step 1: UserErrorCode에 birthday 에러코드 추가**

`UserErrorCode.kt`의 프로필 섹션(USER-013 `REGION_NOT_RESOLVED` 줄 다음)에 두 줄을 추가한다:

```kotlin
	REGION_NOT_RESOLVED("USER-013", "활동지역을 인식하지 못했습니다. 지원하는 지역(시/도)을 입력해 주세요.", HttpStatus.BAD_REQUEST),
	BIRTHDAY_REQUIRED("USER-014", "생년월일을 입력해 주세요.", HttpStatus.BAD_REQUEST),
	INVALID_BIRTHDAY("USER-015", "생년월일이 올바르지 않습니다. 만 19세 이상 100세 이하만 가입할 수 있습니다.", HttpStatus.BAD_REQUEST),
```

- [ ] **Step 2: UserDetail 도메인 — age→birthday, 검증·파생 추가**

`UserDetail.kt`를 다음과 같이 바꾼다.

(2-1) import 추가:
```kotlin
import com.org.oneulsogae.core.common.time.ageAt
import java.time.LocalDate
```

(2-2) 프로퍼티 `age: Int? = null`(28번 줄) → `birthday: LocalDate? = null`:
```kotlin
	val birthday: LocalDate? = null,
```

(2-3) `initProfile` 시그니처: 파라미터 `age: Int?` → `birthday: LocalDate?`, 그리고 마지막 파라미터로 `today: LocalDate` 추가. `copy(... age = age ...)`를 `birthday = birthday`로, 검증 호출에 `validateBirthday(today)`를 추가한다. 전체 함수 본문:

```kotlin
	fun initProfile(
		nickname: String?,
		birthday: LocalDate?,
		height: Int?,
		gender: Gender?,
		phoneNumber: String?,
		job: String?,
		activityArea: String?,
		introduction: String?,
		traits: List<String>,
		interests: List<String>,
		companyEmail: String,
		maritalStatus: MaritalStatus?,
		smokingStatus: SmokingStatus?,
		religion: Religion?,
		drinkingStatus: DrinkingStatus?,
		bodyType: BodyType?,
		today: LocalDate,
	): UserDetail {
		val updated: UserDetail = copy(
			nickname = nickname,
			birthday = birthday,
			height = height,
			gender = gender,
			phoneNumber = phoneNumber,
			job = job,
			activityArea = activityArea,
			regionCode = Region.resolveAreaCode(activityArea),
			introduction = introduction,
			traits = traits,
			interests = interests,
			companyEmail = companyEmail,
			maritalStatus = maritalStatus,
			smokingStatus = smokingStatus,
			religion = religion,
			drinkingStatus = drinkingStatus,
			bodyType = bodyType,
		).assignProfileImageCodeIfAbsent()
		updated.validateBirthday(today)
		updated.validateMatchProfile()
		return updated
	}
```

(2-4) `validateMatchProfile()` 바로 위(또는 아래)에 `validateBirthday`와 표시용 `age()`를 추가한다:

```kotlin
	/**
	 * 생년월일이 채워졌고 만 나이가 가입 허용 범위(만 19~100세)인지 검증한다. (온보딩 프로필 입력 시 호출)
	 * 미래 날짜는 만 나이가 19 미만이 되어 함께 걸러진다.
	 */
	private fun validateBirthday(today: LocalDate) {
		val birthday: LocalDate = birthday ?: throw BusinessException(UserErrorCode.BIRTHDAY_REQUIRED)
		val age: Int = birthday.ageAt(today)
		if (age < MIN_AGE || age > MAX_AGE) {
			throw BusinessException(UserErrorCode.INVALID_BIRTHDAY)
		}
	}

	/** 표시용 만 나이. 생년월일이 없으면 null. (응답 렌더링 시 기준일을 넘겨 계산한다) */
	fun age(today: LocalDate): Int? = birthday?.ageAt(today)
```

(2-5) `matchProfileSnapshotOrNull`의 `age = age ?: return null`(170번 줄) → `birthday = birthday ?: return null`:
```kotlin
			birthday = birthday ?: return null,
```

(2-6) companion object에 상수 추가(기존 `PROFILE_IMAGE_CODE_COUNT` 근처):
```kotlin
		/** 가입 허용 만 나이 하한/상한. */
		private const val MIN_AGE: Int = 19
		private const val MAX_AGE: Int = 100
```

(2-7) 클래스 KDoc(19번 줄)의 "나이/키/성별" 표현은 의미가 유지되므로 그대로 둔다. (수술적 변경)

- [ ] **Step 3: UpdateUserDetailCommand — age→birthday**

`UpdateUserDetailCommand.kt`: import `java.time.LocalDate` 추가, `val age: Int` → `val birthday: LocalDate`:
```kotlin
import java.time.LocalDate
// ...
	val birthday: LocalDate,
```

- [ ] **Step 4: UpdateUserDetailService — TimeGenerator 주입, today 전달**

`UpdateUserDetailService.kt`:

(4-1) import 추가:
```kotlin
import com.org.oneulsogae.core.common.time.TimeGenerator
```

(4-2) 생성자에 `TimeGenerator` 주입:
```kotlin
@Service
class UpdateUserDetailService(
	private val getUserDetailPort: GetUserDetailPort,
	private val saveUserDetailPort: SaveUserDetailPort,
	private val domainEventPublisher: DomainEventPublisher,
	private val timeGenerator: TimeGenerator,
) : UpdateUserDetailUseCase {
```

(4-3) `initProfile` 호출에서 `age = command.age` → `birthday = command.birthday`로 바꾸고 `today = timeGenerator.today()`를 마지막 인자로 추가:
```kotlin
		val updated: UserDetail = existing.initProfile(
			nickname = command.nickname,
			birthday = command.birthday,
			height = command.height,
			gender = command.gender,
			phoneNumber = command.phoneNumber,
			job = command.job,
			activityArea = command.activityArea,
			introduction = command.introduction,
			traits = command.traits,
			interests = command.interests,
			companyEmail = command.companyEmail,
			maritalStatus = command.maritalStatus,
			smokingStatus = command.smokingStatus,
			religion = command.religion,
			drinkingStatus = command.drinkingStatus,
			bodyType = command.bodyType,
			today = timeGenerator.today(),
		)
```

- [ ] **Step 5: MatchProfileSnapshot — age→birthday**

`MatchProfileSnapshot.kt`: import `java.time.LocalDate` 추가, `val age: Int`(14번 줄) → `val birthday: LocalDate`:
```kotlin
import java.time.LocalDate
// ...
	val gender: Gender,
	val birthday: LocalDate,
	val regionCode: Int,
```

- [ ] **Step 6: MatchUser — age→birthday**

`MatchUser.kt`: import `java.time.LocalDate`는 이미 LocalDateTime 있음 → `import java.time.LocalDate` 추가. 프로퍼티 `val age: Int`(16번 줄) → `val birthday: LocalDate`, `from()`의 `age = snapshot.age` → `birthday = snapshot.birthday`:
```kotlin
import java.time.LocalDate
// ...
	val gender: Gender,
	val birthday: LocalDate,
// ...
				birthday = snapshot.birthday,
```

- [ ] **Step 7: 읽기모델 5종 — age→birthday**

각 read model의 `age` 필드를 `birthday`로 바꾸고 `import java.time.LocalDate`를 추가한다. 필드 순서·위치는 그대로 두고 타입/이름만 바꾼다.

- `UserDetailView.kt`: `val age: Int?` → `val birthday: LocalDate?`
- `InvitableUser.kt`: `val age: Int` → `val birthday: LocalDate`
- `ReceivedInvitation.kt` (data class `ReceivedInvitationInviter`): `val age: Int` → `val birthday: LocalDate` (KDoc의 "나이"는 그대로 둔다)
- `SentInvitation.kt` (data class `SentInvitationMember`): `val age: Int` → `val birthday: LocalDate`
- `MatchWithPartner.kt`: `val age: Int?` → `val birthday: LocalDate?`

- [ ] **Step 8: UserDetailFixture — age→birthday**

`UserDetailFixture.kt`: import `java.time.LocalDate` 추가. 파라미터 `age: Int? = 30` → `birthday: LocalDate? = LocalDate.of(1995, 1, 1)`, 생성자 인자 `age = age` → `birthday = birthday`:
```kotlin
import java.time.LocalDate
// ...
		birthday: LocalDate? = LocalDate.of(1995, 1, 1),
// ...
			birthday = birthday,
```

- [ ] **Step 9: core 빌드 확인**

Run: `./gradlew :oneulsogae-core:build`
Expected: BUILD SUCCESSFUL (core 자체 테스트 없음 / 컴파일 + testFixtures 통과)

- [ ] **Step 10: 커밋**

```bash
git add oneulsogae-core
git commit -m "refactor(core): age→birthday 전환 (도메인·포트·서비스·이벤트·읽기모델·검증)"
```

---

### Task 3: scheduler `MatchBatchTarget`에서 age 필드 제거

배치는 gender·regionCode로만 그룹핑하고 나이를 사용하지 않으므로(미사용), birthday로 스왑하지 않고 필드를 제거한다.

**Files:**
- Modify: `oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/match/query/dto/MatchBatchTarget.kt`

**Interfaces:**
- Produces: `MatchBatchTarget(userId, lastLoginAt, gender, maritalStatus, regionCode)` — age 파라미터 없음.

- [ ] **Step 1: age 필드 제거**

`MatchBatchTarget.kt`에서 `val age: Int? = null,` 줄을 삭제하고, KDoc의 `[gender]/[age]/[maritalStatus]/[regionCode]`를 `[gender]/[maritalStatus]/[regionCode]`로 정정한다:

```kotlin
/**
 * 매칭 배치 대상 한 건. 다음 커서 산출에 필요한 [lastLoginAt]과
 * 매칭 판단에 필요한 프로필([gender]/[maritalStatus]/[regionCode])을 함께 담는다.
 */
data class MatchBatchTarget(
	val userId: Long,
	val lastLoginAt: LocalDateTime,
	val gender: Gender? = null,
	val maritalStatus: MaritalStatus? = null,
	val regionCode: Int? = null,
)
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :oneulsogae-scheduler:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add oneulsogae-scheduler
git commit -m "refactor(scheduler): MatchBatchTarget 미사용 age 필드 제거"
```

---

### Task 4: infra 엔티티·매퍼·DAO·픽스처 birthday 전환

**Files:**
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/entity/UserDetailEntity.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/entity/MatchUserEntity.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/mapper/UserDetailMapper.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/mapper/MatchUserMapper.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/query/SearchInvitableUsersDaoImpl.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/query/GetReceivedInvitationsDaoImpl.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/query/GetSentInvitationDaoImpl.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/query/GetMatchWithPartnerDaoImpl.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/query/GetMatchBatchTargetDaoImpl.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/query/GetUserDetailDaoImpl.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/query/GetUserWithDetailDaoImpl.kt`
- Modify: `oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/UserDetailEntityFixture.kt`
- Modify: `oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/MatchUserEntityFixture.kt`

**Interfaces:**
- Consumes: core 읽기모델/도메인의 `birthday` 필드(Task 2), `MatchBatchTarget`의 age 제거(Task 3).
- Produces: `user_details.birthday DATE`(nullable), `match_user.birthday DATE`(not null) 컬럼. Q클래스 `QUserDetailEntity.birthday`, `QMatchUserEntity.birthday`(엔티티 필드명 변경으로 재생성됨).

- [ ] **Step 1: UserDetailEntity — age 컬럼 → birthday**

`UserDetailEntity.kt`: import `java.time.LocalDate` 추가. 45~47번 줄을 바꾼다:
```kotlin
	/** 생년월일. */
	@Column(name = "birthday")
	var birthday: LocalDate? = null,
```

- [ ] **Step 2: MatchUserEntity — age 컬럼 → birthday**

`MatchUserEntity.kt`: import `java.time.LocalDate` 추가(이미 LocalDateTime import 있음). 52~53번 줄을 바꾼다:
```kotlin
	@Column(name = "birthday", nullable = false)
	var birthday: LocalDate,
```

- [ ] **Step 3: UserDetailMapper — age↔birthday**

`UserDetailMapper.kt`: `toDomain()`의 `age = age` → `birthday = birthday`, `toEntity()`의 `age = age` → `birthday = birthday`로 바꾼다(두 곳).

- [ ] **Step 4: MatchUserMapper — age↔birthday**

`MatchUserMapper.kt`: `toDomain()`의 `age = age` → `birthday = birthday`, `toEntity()`의 `age = age` → `birthday = birthday`, `applyFrom()`의 `age = matchUser.age` → `birthday = matchUser.birthday`로 바꾼다(세 곳).

- [ ] **Step 5: 조회 DAO projection — `.age` → `.birthday`**

QueryDSL `Projections.constructor(...)`의 age 컬럼 경로를 birthday로 바꾼다(읽기모델 필드 순서와 1:1 대응 유지):

- `SearchInvitableUsersDaoImpl.kt`: `candidate.age` → `candidate.birthday`
- `GetReceivedInvitationsDaoImpl.kt`: `inviterMatch.age` → `inviterMatch.birthday`
- `GetSentInvitationDaoImpl.kt`: `matchUser.age` → `matchUser.birthday`
- `GetMatchWithPartnerDaoImpl.kt`: `partnerDetail.age` → `partnerDetail.birthday`
- `GetUserDetailDaoImpl.kt`: `detail.age` → `detail.birthday`
- `GetUserWithDetailDaoImpl.kt`: `detail.age` → `detail.birthday`

- [ ] **Step 6: GetMatchBatchTargetDaoImpl — age projection 제거**

`GetMatchBatchTargetDaoImpl.kt`의 `Projections.constructor(MatchBatchTarget::class.java, ...)`에서 `matchUser.age,` 줄을 삭제한다(생성자 인자가 `userId, lastLoginAt, gender, maritalStatus, regionCode` 순이 되도록):
```kotlin
				Projections.constructor(
					MatchBatchTarget::class.java,
					matchUser.userId,
					matchUser.lastLoginAt,
					matchUser.gender,
					matchUser.maritalStatus,
					matchUser.regionCode,
				),
```

- [ ] **Step 7: UserDetailEntityFixture — age→birthday**

`UserDetailEntityFixture.kt`: import `java.time.LocalDate` 추가. 파라미터 `age: Int? = 28` → `birthday: LocalDate? = LocalDate.of(1996, 1, 1)`, 생성자 인자 `age = age` → `birthday = birthday`. KDoc "닉네임·성별·나이"는 그대로 둔다.

- [ ] **Step 8: MatchUserEntityFixture — age→birthday**

`MatchUserEntityFixture.kt`: import `java.time.LocalDate` 추가. 파라미터 `age: Int = 28` → `birthday: LocalDate = LocalDate.of(1996, 1, 1)`, 생성자 인자 `age = age` → `birthday = birthday`.

- [ ] **Step 9: infra 빌드 확인**

Run: `./gradlew :oneulsogae-infra:build`
Expected: BUILD SUCCESSFUL (infra 자체 테스트 + testFixtures 컴파일 통과. Q클래스가 birthday로 재생성됨)

- [ ] **Step 10: 커밋**

```bash
git add oneulsogae-infra
git commit -m "refactor(infra): age→birthday 전환 (엔티티·매퍼·DAO·픽스처)"
```

---

### Task 5: api 요청 DTO·응답 팩토리·컨트롤러 — 입력 birthday, 응답 파생 age

**Files:**
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/user/request/UpdateUserDetailRequest.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/user/response/UserProfileResponse.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/response/InvitableUserResponse.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/response/ReceivedInvitationResponse.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/response/SentInvitationResponse.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/response/MatchResponse.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/user/UserProfileController.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/MatchController.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/TeamController.kt`

**Interfaces:**
- Consumes: 읽기모델/도메인의 `birthday`(Task 2), `LocalDate.ageAt(today)`(Task 1), `TimeGenerator.today()`.
- Produces: 응답 JSON은 기존과 동일한 `age` 필드. 응답 팩토리 `of(...)`/`listOf(...)`가 `today: LocalDate`를 받아 birthday에서 age를 계산.

- [ ] **Step 1: UpdateUserDetailRequest — age→birthday 입력**

`UpdateUserDetailRequest.kt`:

(1-1) import 변경: `import java.time.LocalDate` 추가. (`Min`/`Max`는 height에서 계속 쓰므로 유지)

(1-2) 28~31번 줄(age 필드) 교체 — 나이 범위 검증은 도메인으로 옮기므로 `@NotNull`만 남긴다:
```kotlin
	@field:NotNull(message = "생년월일은 필수입니다.")
	val birthday: LocalDate? = null,
```

(1-3) `toCommand()`의 `age = age!!` → `birthday = birthday!!`:
```kotlin
			birthday = birthday!!,
```

- [ ] **Step 2: UserProfileResponse — today로 age 파생**

`UserProfileResponse.kt`:

(2-1) import 추가:
```kotlin
import com.org.oneulsogae.core.common.time.ageAt
import java.time.LocalDate
```

(2-2) `of(detail: UserDetailView)` → `of(detail: UserDetailView, today: LocalDate)`, `age = detail.age` → `age = detail.birthday?.ageAt(today)`.

(2-3) `of(detail: UserDetail)` → `of(detail: UserDetail, today: LocalDate)`, `age = detail.age` → `age = detail.age(today)`.

(필드 `age: Int?`는 그대로. 두 팩토리 모두 시그니처에 `today` 추가)

- [ ] **Step 3: InvitableUserResponse — today로 age 파생**

`InvitableUserResponse.kt`: import `com.org.oneulsogae.core.common.time.ageAt`, `java.time.LocalDate` 추가.
- `of(user: InvitableUser)` → `of(user: InvitableUser, today: LocalDate)`, `age = user.age` → `age = user.birthday.ageAt(today)`.
- `listOf(users: List<InvitableUser>)` → `listOf(users: List<InvitableUser>, today: LocalDate)`, 본문 `users.map { of(it, today) }`.

- [ ] **Step 4: ReceivedInvitationResponse — today로 age 파생**

`ReceivedInvitationResponse.kt`: import `com.org.oneulsogae.core.common.time.ageAt`, `java.time.LocalDate` 추가.
- `of(invitation: ReceivedInvitation)` → `of(invitation: ReceivedInvitation, today: LocalDate)`, inviter 매핑의 `age = inviter.age` → `age = inviter.birthday.ageAt(today)`.
- `listOf(invitations: List<ReceivedInvitation>)` → `listOf(invitations: List<ReceivedInvitation>, today: LocalDate)`, 본문 `invitations.map { of(it, today) }`.

- [ ] **Step 5: SentInvitationResponse — today로 age 파생**

`SentInvitationResponse.kt`: import `com.org.oneulsogae.core.common.time.ageAt`, `java.time.LocalDate` 추가.
- `of(invitation: SentInvitation?)` → `of(invitation: SentInvitation?, today: LocalDate)`, member 매핑의 `age = member.age` → `age = member.birthday.ageAt(today)`.

- [ ] **Step 6: MatchResponse / PartnerResponse — today로 age 파생**

`MatchResponse.kt`: import `com.org.oneulsogae.core.common.time.ageAt`, `java.time.LocalDate` 추가.
- `MatchResponse.of(matchWithPartner: MatchWithPartner)` → `of(matchWithPartner: MatchWithPartner, today: LocalDate)`, `partner = PartnerResponse.of(matchWithPartner, today)`.
- `MatchResponse.listOf(matches: List<MatchWithPartner>)` → `listOf(matches: List<MatchWithPartner>, today: LocalDate)`, 본문 `matches.map { of(it, today) }`.
- `PartnerResponse.of(matchWithPartner: MatchWithPartner)` → `of(matchWithPartner: MatchWithPartner, today: LocalDate)`, `age = matchWithPartner.age` → `age = matchWithPartner.birthday?.ageAt(today)`.

- [ ] **Step 7: UserProfileController — TimeGenerator 주입, today 전달**

`UserProfileController.kt`: import `com.org.oneulsogae.core.common.time.TimeGenerator` 추가. 생성자에 `private val timeGenerator: TimeGenerator` 주입. 두 호출 변경:
```kotlin
		ApiResponse.success(UserProfileResponse.of(getUserDetailUseCase.getByUserId(user.id), timeGenerator.today()))
// ...
		ApiResponse.success(UserProfileResponse.of(updateProfileUseCase.updateProfile(user.id, request.toCommand()), timeGenerator.today()))
```

- [ ] **Step 8: MatchController — TimeGenerator 주입, today 전달**

`MatchController.kt`: import `com.org.oneulsogae.core.common.time.TimeGenerator` 추가. 생성자에 `private val timeGenerator: TimeGenerator` 주입. `myMatches` 호출 변경:
```kotlin
		ApiResponse.success(MatchResponse.listOf(getMatchesUseCase.getMatches(user.id, isAfterOnboarding), timeGenerator.today()))
```

- [ ] **Step 9: TeamController — TimeGenerator 주입, today 전달**

`TeamController.kt`: import `com.org.oneulsogae.core.common.time.TimeGenerator` 추가. 생성자에 `private val timeGenerator: TimeGenerator` 주입. 세 호출 변경:
```kotlin
		ApiResponse.success(SentInvitationResponse.of(getSentInvitationUseCase.get(user.id), timeGenerator.today()))
// ...
		ApiResponse.success(ReceivedInvitationResponse.listOf(getReceivedInvitationsUseCase.get(user.id), timeGenerator.today()))
// ...
		ApiResponse.success(InvitableUserResponse.listOf(searchInvitableUsersUseCase.search(user.id, request.nickname!!), timeGenerator.today()))
```

- [ ] **Step 10: api main 컴파일 확인**

Run: `./gradlew :oneulsogae-api:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 11: 커밋**

```bash
git add oneulsogae-api/src/main
git commit -m "refactor(api): 입력 birthday 수용, 응답 age를 birthday에서 today 기준 파생"
```

---

### Task 6: 도메인 유닛 테스트 — birthday 검증·파생

도메인 단위 테스트는 `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/`에 있다(Kotest). 고정 `today`로 결정적으로 검증한다.

**Files:**
- Modify: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/user/UserDetailTest.kt`
- Modify: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/MatchUserTest.kt`

**Interfaces:**
- Consumes: `UserDetailFixture.create(birthday = ...)`, `UserDetail.initProfile(..., today)`, `UserDetail.age(today)`, `UserDetail.matchProfileSnapshotOrNull(...)`, `UserErrorCode.INVALID_BIRTHDAY`/`BIRTHDAY_REQUIRED`.

- [ ] **Step 1: 기존 age 참조를 birthday로 갱신하고 새 검증 케이스 추가 (UserDetailTest)**

먼저 파일을 읽어 기존 구조(테스트 스타일·헬퍼)를 따른다. 기존에 `age = ...`를 쓰던 픽스처 호출은 `birthday = LocalDate.of(...)`로 바꾸고, 스냅샷 단언이 `age`를 보던 곳은 `birthday`로 바꾼다. 그리고 아래 검증 케이스를 추가한다(기존 스타일에 맞춰 `StringSpec`/`DescribeSpec` 중 파일이 쓰는 형식으로). 기준일은 `LocalDate.of(2026, 6, 22)` 고정.

추가할 동작(테스트 본문은 파일의 기존 스펙 스타일에 맞춘다):
```kotlin
// today 고정
val today: LocalDate = LocalDate.of(2026, 6, 22)

// 만 19세 경계: 2007-06-22 생 → 정확히 19세 → 통과
// 만 18세: 2007-06-23 생 → 18세 → INVALID_BIRTHDAY
// 만 100세 경계: 1926-06-22 생 → 100세 → 통과
// 만 101세: 1925-06-21 생 → 101세 → INVALID_BIRTHDAY
// 미래 날짜: 2030-01-01 생 → 음수 나이 → INVALID_BIRTHDAY
```

예시 케이스(파일 스타일에 맞춰 작성):
```kotlin
"initProfile: 만 19세 미만이면 INVALID_BIRTHDAY" {
	val detail: UserDetail = UserDetailFixture.create()
	val ex: BusinessException = shouldThrow<BusinessException> {
		detail.initProfile(
			nickname = "닉", birthday = LocalDate.of(2007, 6, 23), height = 175,
			gender = Gender.MALE, phoneNumber = "010-0000-0000", job = "개발자",
			activityArea = "서울특별시 강남구", introduction = "소개",
			traits = listOf("성실함"), interests = listOf("영화"),
			companyEmail = "a@b.com", maritalStatus = MaritalStatus.SINGLE,
			smokingStatus = SmokingStatus.NON_SMOKER, religion = Religion.NONE,
			drinkingStatus = DrinkingStatus.SOMETIMES, bodyType = BodyType.MALE_NORMAL,
			today = today,
		)
	}
	ex.errorCode shouldBe UserErrorCode.INVALID_BIRTHDAY
}

"initProfile: 만 19세 경계는 통과" {
	val detail: UserDetail = UserDetailFixture.create()
	val updated: UserDetail = detail.initProfile(
		nickname = "닉", birthday = LocalDate.of(2007, 6, 22), height = 175,
		gender = Gender.MALE, phoneNumber = "010-0000-0000", job = "개발자",
		activityArea = "서울특별시 강남구", introduction = "소개",
		traits = listOf("성실함"), interests = listOf("영화"),
		companyEmail = "a@b.com", maritalStatus = MaritalStatus.SINGLE,
		smokingStatus = SmokingStatus.NON_SMOKER, religion = Religion.NONE,
		drinkingStatus = DrinkingStatus.SOMETIMES, bodyType = BodyType.MALE_NORMAL,
		today = today,
	)
	updated.age(today) shouldBe 19
}

"age(today): 생년월일이 없으면 null" {
	UserDetailFixture.create(birthday = null).age(today) shouldBe null
}
```
> 참고: `BusinessException`이 `errorCode`를 노출하는지 파일의 기존 단언 방식을 따른다. 기존 테스트가 다른 방식으로 에러코드를 확인하면 그 방식을 쓴다.

- [ ] **Step 2: MatchUserTest — 스냅샷 birthday 반영**

파일을 읽고, `MatchProfileSnapshot`/`MatchUser`에서 `age`를 단언/구성하던 부분을 `birthday = LocalDate.of(...)`로 바꾼다. `MatchUser.from(userId, snapshot)`가 `snapshot.birthday`를 그대로 담는지 확인하는 단언으로 갱신한다.

- [ ] **Step 3: 유닛 테스트 실행**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.*"`
Expected: PASS (새 검증·파생 케이스 포함)

- [ ] **Step 4: 커밋**

```bash
git add oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain
git commit -m "test: birthday 검증·만나이 파생 유닛 테스트"
```

---

### Task 7: E2E 테스트 — 온보딩 입력 birthday, 응답 age 파생 단언

E2E는 시각 고정 빌트인이 없어 실 시각을 쓴다. 따라서 응답 `age` 단언은 픽스처 생년월일과 `LocalDate.now()`로 계산한 기대값으로 비교한다(같은 날 실행 → 동일). 기대값 계산엔 프로덕션 확장 `LocalDate.ageAt(...)`를 재사용한다.

**Files:**
- Modify: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/user/RequestCompanyEmailVerificationE2ETest.kt`
- Modify: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/SearchInvitableUsersE2ETest.kt`
- Modify: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/GetReceivedInvitationsE2ETest.kt`
- Modify: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/GetSentInvitationE2ETest.kt`
- Modify: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/GetMatchesE2ETest.kt`
- Modify: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/MatchUserSyncE2ETest.kt`

**Interfaces:**
- Consumes: 픽스처 `UserDetailEntityFixture.create(birthday = ...)`, `MatchUserEntityFixture.create(birthday = ...)`, `LocalDate.ageAt(today)`.

- [ ] **Step 1: 온보딩 요청 본문 age→birthday (RequestCompanyEmailVerificationE2ETest)**

파일을 읽고, 온보딩 요청 JSON의 `"age": 30`을 `"birthday": "1995-01-01"`로 바꾼다. 응답에서 프로필 age를 단언하는 부분이 있으면 `Period.between(LocalDate.of(1995,1,1), LocalDate.now()).years`(또는 `LocalDate.of(1995,1,1).ageAt(LocalDate.now())`)로 기대값을 계산해 비교한다. 만약 나이 범위 검증(19~100)을 확인하는 케이스가 있으면 잘못된 생년월일(예: 미래 `"2030-01-01"`)로 400을 단언하도록 바꾼다.

- [ ] **Step 2: 매칭/초대 E2E — 픽스처 birthday + 파생 age 단언**

각 파일을 읽고 다음 규칙으로 바꾼다:

(a) 픽스처 호출에서 `age = N`을 `birthday = LocalDate.of(Y, M, D)`로 바꾼다. 기존에 특정 나이를 의도한 케이스는 "오늘 기준 그 나이가 되는" 생년월일을 고른다. 안정성을 위해 **테스트 상단에 기준 생년월일 상수**를 두고 기대 나이를 계산한다:
```kotlin
private val BIRTHDAY: LocalDate = LocalDate.of(1996, 1, 1)
private val EXPECTED_AGE: Int = BIRTHDAY.ageAt(LocalDate.now())
```
(b) JSON 경로 age 단언을 계산값으로 바꾼다. 예:
- `SearchInvitableUsersE2ETest`: `body("data.age", containsInAnyOrder(28, 28, 28))` → 픽스처를 동일 `BIRTHDAY`로 맞추고 `body("data.age", containsInAnyOrder(EXPECTED_AGE, EXPECTED_AGE, EXPECTED_AGE))`.
- `GetReceivedInvitationsE2ETest`: `body("data[0].inviters[0].age", N)` → 해당 inviter 픽스처를 `BIRTHDAY`로 맞추고 `EXPECTED_AGE`로 단언.
- `GetSentInvitationE2ETest`: `body("data.members[0].age", N)` → `EXPECTED_AGE`.
- `GetMatchesE2ETest`: 파트너 픽스처 `age`를 `birthday`로 바꾸고 `partner.age`(또는 해당 경로) 단언을 `EXPECTED_AGE`로.
- `MatchUserSyncE2ETest`: 동기화 후 match_user에 반영되는 값 단언이 age였다면, 픽스처/요청을 birthday로 바꾸고 응답·동기화 결과의 age 단언을 `EXPECTED_AGE`로. (직접 DB 조회 단언이 있으면 birthday 컬럼 기준으로 갱신)

> 단언이 RestAssured Hamcrest `equalTo(Int)`를 쓰면 `equalTo(EXPECTED_AGE)`로 바꾼다. import에 `java.time.LocalDate`, `com.org.oneulsogae.core.common.time.ageAt` 추가.

- [ ] **Step 3: 전체 빌드 (유닛 + E2E)**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — 모든 모듈 컴파일·유닛·E2E 통과. (Testcontainers MySQL 기동 필요)

- [ ] **Step 4: 커밋**

```bash
git add oneulsogae-api/src/test
git commit -m "test(e2e): 온보딩 birthday 입력 + 응답 파생 age 단언 갱신"
```

---

## Self-Review

**Spec coverage** (`docs/superpowers/specs/2026-06-22-age-to-birthday-design.md`):
- 입력 birthday + @NotNull + 도메인 만19~100 검증 → Task 5 Step1, Task 2 Step2/Step6. ✓
- 도메인 birthday + age(today) 파생 + 스냅샷 birthday → Task 2. ✓
- DB age 컬럼 제거 + birthday DATE (ddl-auto) → Task 4 Step1/2. ✓
- 매칭 도메인/이벤트 birthday → Task 2 Step5/6. ✓
- 읽기모델 birthday + DAO projection 스왑 → Task 2 Step7, Task 4 Step5. ✓
- MatchBatchTarget age 제거 → Task 3, Task 4 Step6. ✓
- 응답 age 계약 유지 + today 파생 → Task 5 Step2~9. ✓
- 유닛/E2E 테스트 + 픽스처 → Task 2 Step8, Task 4 Step7/8, Task 6, Task 7. ✓
- 프론트 영향(온보딩 폼만): 백엔드 범위 밖 — 계획에 작업 없음(의도). ✓

**Placeholder scan:** 프로덕션 파일은 전부 정확한 코드 포함. 테스트 파일(Task 6/7)은 기존 파일 구조에 맞춰야 하므로 "파일을 읽고 규칙대로" 지시 + 핵심 케이스 코드 제공. 이는 기존 테스트 스타일을 보존하기 위한 의도된 지시이며 빈 칸이 아니다.

**Type consistency:** `birthday: LocalDate`(필수: command/snapshot/MatchUser/InvitableUser/ReceivedInvitationInviter/SentInvitationMember/MatchUserEntity) vs `LocalDate?`(nullable: UserDetail/UserDetailView/MatchWithPartner/UserDetailEntity/요청 DTO). 응답 팩토리 시그니처는 모두 `today: LocalDate` 추가로 일관. `ageAt(today: LocalDate): Int`, `UserDetail.age(today): Int?` 일관.

**브리틀니스 주의(의도된 트레이드오프):** E2E는 시각 고정 빌트인이 없어 응답 age를 `픽스처 생년월일.ageAt(LocalDate.now())`로 계산해 단언한다. 같은 날 내 실행에서 결정적이며, 만 나이 산술의 정확성은 Task 6 유닛 테스트가 고정 `today`로 보장한다.
