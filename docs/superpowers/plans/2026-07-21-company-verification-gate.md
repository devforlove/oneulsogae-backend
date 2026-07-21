# 회사 인증 게이트 (소개·미팅·라운지) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 소개·미팅·라운지 셀소 리스트 응답에 로그인 사용자 본인의 회사 인증 여부 플래그를 내려주고, 세 탭의 관심요청 API에서 미인증 사용자를 403으로 차단한다.

**Architecture:** 인증 판정(`UserDetail.companyName != null`)을 user query in-port `CheckCompanyVerifiedUseCase` 한 곳에 캡슐화하고, 세 도메인의 조회 서비스(플래그 채우기)와 명령 서비스(검증)가 이 in-port를 주입받는다. 새 dao/out-port/테이블은 만들지 않고 기존 `GetUserDetailDao`를 재사용한다.

**Tech Stack:** Kotlin 2.2.21 / JVM 21 / Spring Boot 4.0.6 / Spring Data JPA + QueryDSL / Kotest(E2E, Testcontainers) / RestAssured

**설계 문서:** `docs/superpowers/specs/2026-07-21-company-verification-gate-design.md`

## Global Constraints

- **응답·주석·커밋 메시지는 한국어로 작성한다.**
- **타입 명시**: 변수·반환 타입·람다 파라미터 타입을 생략하지 않는다. 표현식 본문 함수도 반환 타입을 적는다.
- **들여쓰기는 탭(tab) 1개**를 쓴다. (기존 파일 전부 탭)
- **커밋 메시지 형식**: `<type>(<domain>): <설명>` — 예: `feat(user): 회사 인증 여부 조회 인포트 추가`
- **CQS**: 조회 서비스는 `@Transactional(readOnly = true)`, 명령 서비스는 `@Transactional`.
- **도메인 간 참조**: 다른 도메인은 in-port `UseCase`로만 주입한다. 다른 도메인의 dao/out-port/구현체를 직접 주입하지 않는다.
- **범위**: `oneulsogae-backend`만 수정한다. 프론트엔드는 손대지 않는다.
- **판정 기준**: 회사 인증 여부 = `UserDetailView.companyName != null`. 이 판정은 `CheckCompanyVerifiedService` 한 곳에만 존재해야 하며, 다른 파일에서 `companyName != null`을 다시 쓰지 않는다.
- **에러코드 값(정확히 이 문자열)**: `COMPANY_NOT_VERIFIED("USER-035", "회사 인증이 완료된 사용자만 이용할 수 있습니다.", HttpStatus.FORBIDDEN)`
- **응답 필드명(정확히 이 이름)**: `companyVerified`

### 테스트 실행 명령

E2E는 Testcontainers로 MySQL을 띄우므로 Docker가 실행 중이어야 한다. 단일 스펙 실행:

```bash
./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.lounge.GetSelfIntroPostsE2ETest"
```

### 이 프로젝트의 테스트 컨벤션 (숙지 필수)

- 테스트는 Kotest `DescribeSpec` 스타일이며 `AbstractIntegrationSupport({ ... })`를 상속한다. 본문은 생성자 인자로 넘기는 람다다.
- HTTP 호출은 `get("/path") { bearer(accessTokenFor(userId)) } expect { status(200); body("...", value) }` DSL을 쓴다.
- 데이터 시딩은 `IntegrationUtil.persist(...)` + `infra` `testFixtures`의 `*EntityFixture`를 쓴다. 리포지토리를 직접 주입하지 않는다.
- 응답 봉투는 `{ success, data, error }`다. 성공 본문은 `data.…`, 실패는 `error.code`로 검증한다.
- 정리는 `afterTest { IntegrationUtil.deleteAll(QXxxEntity.xxxEntity) }`로 한다.

---

## File Structure

**신규 생성 (3개)**
| 파일 | 책임 |
|---|---|
| `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/query/service/port/in/CheckCompanyVerifiedUseCase.kt` | 회사 인증 여부 조회/검증 in-port |
| `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/query/service/CheckCompanyVerifiedService.kt` | 위 in-port 구현. 판정 규칙의 유일한 소유자 |
| `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/solomatch/query/dto/MyMatches.kt` | 소개 목록 read model (플래그 + 목록) |

**수정**
| 파일 | 변경 |
|---|---|
| `core/user/UserErrorCode.kt` | `COMPANY_NOT_VERIFIED` 추가 |
| `core/lounge/query/dto/SelfIntroPostPage.kt` | `companyVerified` 필드 + `withCompanyVerified` |
| `core/lounge/query/service/GetSelfIntroPostsService.kt` | in-port 주입 + 플래그 채우기 |
| `api/lounge/response/SelfIntroPostPageResponse.kt` | `companyVerified` 필드 |
| `core/teammatch/query/dto/MeetingTab.kt` | `companyVerified` 필드 |
| `core/teammatch/query/service/GetMeetingTabService.kt` | in-port 주입 + 플래그 채우기 |
| `api/match/response/MeetingTabResponse.kt` | `companyVerified` 필드 |
| `core/solomatch/query/service/port/in/GetMatchesUseCase.kt` | 반환 타입 `MyMatches` |
| `core/solomatch/query/service/GetMatchesService.kt` | in-port 주입 + `MyMatches` 반환 |
| `api/match/response/MatchResponse.kt` | `MatchListResponse` 추가 |
| `api/match/SoloMatchController.kt` | `myMatches` 반환 타입 변경 |
| `core/solomatch/command/application/SendInterestService.kt` | 검증 한 줄 |
| `core/teammatch/command/application/SendTeamInterestService.kt` | 검증 한 줄 |
| `core/lounge/command/application/RequestLoungeChatService.kt` | 검증 한 줄 |

**테스트 수정/신규**
- 수정: `api/lounge/GetSelfIntroPostsE2ETest.kt`, `api/match/GetMeetingTabE2ETest.kt`, `api/match/GetMatchesE2ETest.kt`, `api/match/ExtraIntroIntegrationTest.kt`, `api/match/MatchUserSyncE2ETest.kt`
- 신규: `api/lounge/RequestLoungeChatE2ETest.kt`·`api/match/SendInterestE2ETest.kt`·`api/match/SendTeamInterestE2ETest.kt`에 미인증 케이스 추가

---

## Task 1: 회사 인증 여부 판정 in-port

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/query/service/port/in/CheckCompanyVerifiedUseCase.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/query/service/CheckCompanyVerifiedService.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/UserErrorCode.kt`

**Interfaces:**
- Consumes: 기존 `com.org.oneulsogae.core.user.query.dao.GetUserDetailDao.findByUserId(userId: Long): UserDetailView?`
- Produces:
  - `CheckCompanyVerifiedUseCase.isCompanyVerified(userId: Long): Boolean`
  - `CheckCompanyVerifiedUseCase.validateCompanyVerified(userId: Long)` — 미인증이면 `BusinessException(UserErrorCode.COMPANY_NOT_VERIFIED)`
  - `UserErrorCode.COMPANY_NOT_VERIFIED` (코드 `USER-035`, 403)

**이 태스크에 단독 테스트가 없는 이유:** 이 프로젝트의 테스트 전략은 "도메인 모델 → Kotest 유닛 / api 경계 → E2E"다. 이 서비스는 도메인 모델이 아니라 dao에 위임하는 얇은 query 서비스라 단독 유닛 테스트를 두지 않는다. 동작 검증은 Task 2~7의 E2E가 담당한다. 이 태스크는 컴파일 통과까지만 확인하고 커밋한다.

- [ ] **Step 1: in-port 인터페이스 생성**

`oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/query/service/port/in/CheckCompanyVerifiedUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.user.query.service.port.`in`

/**
 * 회사 인증(직장 인증) 완료 여부를 확인하는 인포트(유스케이스).
 * 인증이 끝나면 프로필(user_details)의 회사명이 채워지므로, 회사명 보유 여부를 인증 완료 신호로 본다.
 * (이메일 인증·서류 이미지 심사 어느 경로든 승인 시 회사명이 채워진다)
 *
 * 회사 인증을 마친 사용자만 소개·미팅·라운지 기능을 쓸 수 있어, 조회 경로는 [isCompanyVerified]로 화면 분기용
 * 플래그를 얻고 명령 경로는 [validateCompanyVerified]로 미인증 요청을 막는다.
 */
interface CheckCompanyVerifiedUseCase {

	/** 회사 인증을 마쳤는지 여부. 프로필이 없으면 false. (목록 응답의 화면 분기 플래그용) */
	fun isCompanyVerified(userId: Long): Boolean

	/** 회사 인증을 마치지 않았으면 [com.org.oneulsogae.core.user.UserErrorCode.COMPANY_NOT_VERIFIED]를 던진다. (명령 경로 차단용) */
	fun validateCompanyVerified(userId: Long)
}
```

- [ ] **Step 2: 에러코드 추가**

`oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/UserErrorCode.kt`에서 "회사 이메일 인증(직장 인증)" 블록의 마지막 줄인

```kotlin
	COMPANY_NOT_FOUND("USER-034", "확인되지 않는 회사 이메일입니다. 본인 회사의 이메일을 입력해 주세요.", HttpStatus.BAD_REQUEST),
```

바로 아래에 다음 한 줄을 추가한다. (USER-035는 현재 미사용 번호다)

```kotlin
	COMPANY_NOT_VERIFIED("USER-035", "회사 인증이 완료된 사용자만 이용할 수 있습니다.", HttpStatus.FORBIDDEN),
```

- [ ] **Step 3: 구현체 생성**

`oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/query/service/CheckCompanyVerifiedService.kt`:

```kotlin
package com.org.oneulsogae.core.user.query.service

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.query.dao.GetUserDetailDao
import com.org.oneulsogae.core.user.query.dto.UserDetailView
import com.org.oneulsogae.core.user.query.service.port.`in`.CheckCompanyVerifiedUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [CheckCompanyVerifiedUseCase] 구현. 조회 dao([GetUserDetailDao])에만 의존한다.
 * "회사명이 채워졌는가"라는 인증 완료 판정을 이 클래스 한 곳에만 두어, 여러 도메인이 같은 규칙을 각자 인라인하지 않게 한다.
 */
@Service
@Transactional(readOnly = true)
class CheckCompanyVerifiedService(
	private val getUserDetailDao: GetUserDetailDao,
) : CheckCompanyVerifiedUseCase {

	override fun isCompanyVerified(userId: Long): Boolean {
		val detail: UserDetailView? = getUserDetailDao.findByUserId(userId)
		return detail?.companyName != null
	}

	override fun validateCompanyVerified(userId: Long) {
		if (!isCompanyVerified(userId)) {
			throw BusinessException(UserErrorCode.COMPANY_NOT_VERIFIED)
		}
	}
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/
git commit -m "feat(user): 회사 인증 여부 조회·검증 인포트 추가"
```

---

## Task 2: 라운지 셀소 목록에 companyVerified 플래그

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/query/dto/SelfIntroPostPage.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/query/service/GetSelfIntroPostsService.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/lounge/response/SelfIntroPostPageResponse.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/lounge/GetSelfIntroPostsE2ETest.kt`

**Interfaces:**
- Consumes: `CheckCompanyVerifiedUseCase.isCompanyVerified(userId: Long): Boolean` (Task 1)
- Produces: `SelfIntroPostPage.companyVerified: Boolean`, `SelfIntroPostPage.withCompanyVerified(companyVerified: Boolean): SelfIntroPostPage`, 응답 필드 `data.companyVerified`

- [ ] **Step 1: 실패하는 E2E 테스트 두 개 작성**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/lounge/GetSelfIntroPostsE2ETest.kt`의 `describe("GET /lounge/v1/self-intro-posts") { ... }` 블록 **안 마지막**에 아래 두 `context`를 추가한다. (기존 `context`들과 같은 들여쓰기 = 탭 2개)

```kotlin
		context("요청자 프로필에 회사명이 있으면") {
			it("companyVerified=true를 내려준다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-verified")).id!!
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(
						userId = userId,
						nickname = "인증회원",
						gender = Gender.FEMALE,
						birthday = LocalDate.of(1995, 5, 5),
						companyName = "오늘소개",
					),
				)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.get("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(200)
					.body("data.companyVerified", Matchers.equalTo(true))
			}
		}

		context("요청자 프로필에 회사명이 없으면") {
			it("companyVerified=false를 내려준다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-unverified")).id!!
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(
						userId = userId,
						nickname = "미인증회원",
						gender = Gender.FEMALE,
						birthday = LocalDate.of(1995, 5, 5),
						companyName = null,
					),
				)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.get("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(200)
					.body("data.companyVerified", Matchers.equalTo(false))
			}
		}
```

이 파일은 이미 `RestAssured`, `Matchers`, `Gender`, `LocalDate`, `UserEntityFixture`, `UserDetailEntityFixture`, `IntegrationUtil`을 임포트하고 있으므로 **임포트 추가는 필요 없다.** `UserDetailEntityFixture.create`는 `companyName: String? = null` 파라미터를 이미 갖고 있다.

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.lounge.GetSelfIntroPostsE2ETest"`
Expected: FAIL — `data.companyVerified`가 없어 `expected: <true> but was: <null>`

- [ ] **Step 3: read model에 필드 추가**

`SelfIntroPostPage.kt`의 private 생성자에 파라미터를 하나 더 추가한다. `sentPendingChatRequestCount: Int = 0,` 다음 줄에:

```kotlin
	/** 조회한 사용자가 회사 인증을 마쳤는지 여부. 서비스가 채운다. 프론트엔드가 미인증 사용자 화면을 분기하는 데 쓴다. */
	val companyVerified: Boolean = false,
```

그리고 기존 세 개의 `with…` 메서드가 private 생성자를 재호출하므로 **각각에 `companyVerified = companyVerified,`를 추가**한다. 수정 후 세 메서드는 다음과 같다:

```kotlin
	/** 각 항목의 대표 사진 키를 [presign]으로 변환한 열람용 URL을 채운 페이지를 만든다. (사진이 없으면 null 유지) */
	fun withImageUrls(presign: (String) -> String): SelfIntroPostPage =
		SelfIntroPostPage(
			values = values.map { view: SelfIntroPostView ->
				view.copy(imageUrl = view.imageKey?.let(presign))
			},
			hasNext = hasNext,
			receivedPendingChatRequestCount = receivedPendingChatRequestCount,
			sentPendingChatRequestCount = sentPendingChatRequestCount,
			companyVerified = companyVerified,
		)

	/** 각 항목의 작성자 만 나이를 기준일([today])로 채운 페이지를 만든다. */
	fun withAuthorAges(today: LocalDate): SelfIntroPostPage =
		SelfIntroPostPage(
			values = values.map { view: SelfIntroPostView -> view.withAge(today) },
			hasNext = hasNext,
			receivedPendingChatRequestCount = receivedPendingChatRequestCount,
			sentPendingChatRequestCount = sentPendingChatRequestCount,
			companyVerified = companyVerified,
		)

	/** 조회한 사용자의 미수락 신청 건수(받은·보낸)를 반영한 페이지를 만든다. */
	fun withPendingChatRequestCounts(received: Int, sent: Int): SelfIntroPostPage =
		SelfIntroPostPage(
			values = values,
			hasNext = hasNext,
			receivedPendingChatRequestCount = received,
			sentPendingChatRequestCount = sent,
			companyVerified = companyVerified,
		)

	/** 조회한 사용자의 회사 인증 여부를 반영한 페이지를 만든다. */
	fun withCompanyVerified(companyVerified: Boolean): SelfIntroPostPage =
		SelfIntroPostPage(
			values = values,
			hasNext = hasNext,
			receivedPendingChatRequestCount = receivedPendingChatRequestCount,
			sentPendingChatRequestCount = sentPendingChatRequestCount,
			companyVerified = companyVerified,
		)
```

- [ ] **Step 4: 조회 서비스에서 플래그 채우기**

`GetSelfIntroPostsService.kt`:

임포트 추가 (기존 임포트 목록의 알파벳 순서에 맞춰 `LoungeImageUrlPort` 임포트 다음 줄):

```kotlin
import com.org.oneulsogae.core.user.query.service.port.`in`.CheckCompanyVerifiedUseCase
```

생성자에 의존성 추가 — `private val timeGenerator: TimeGenerator,` 다음 줄:

```kotlin
	private val checkCompanyVerifiedUseCase: CheckCompanyVerifiedUseCase,
```

`getPosts` 체인 끝에 한 단계 추가:

```kotlin
	override fun getPosts(userId: Long, cursor: Long?): SelfIntroPostPage {
		val rows: List<SelfIntroPostView> = getSelfIntroPostDao.findPage(cursor, PAGE_SIZE + 1)
		return SelfIntroPostPage.of(rows, PAGE_SIZE)
			.withImageUrls { imageKey: String -> loungeImageUrlPort.presignedGetUrl(imageKey) }
			.withAuthorAges(timeGenerator.today())
			.withPendingChatRequestCounts(
				received = getSelfIntroPostDao.countReceivedPendingChatRequests(userId),
				sent = getSelfIntroPostDao.countSentPendingChatRequests(userId),
			)
			.withCompanyVerified(checkCompanyVerifiedUseCase.isCompanyVerified(userId))
	}
```

클래스 KDoc 마지막 줄에 다음 문장을 덧붙인다:

```
 * 회사 인증 여부는 user 도메인 in-port([CheckCompanyVerifiedUseCase])로 읽어 화면 분기용 플래그로 함께 내려준다.
```

- [ ] **Step 5: 응답 DTO에 필드 추가**

`SelfIntroPostPageResponse.kt`의 `nextCursor: Long?,` 다음 줄에 필드를 추가하고 `of`에 매핑을 추가한다:

```kotlin
	val hasNext: Boolean,
	val nextCursor: Long?,
	/** 요청한 사용자가 회사 인증을 마쳤는지 여부. 미인증이면 프론트엔드가 이용 제한 화면으로 분기한다. */
	val companyVerified: Boolean,
) {
	companion object {

		fun of(page: SelfIntroPostPage): SelfIntroPostPageResponse =
			SelfIntroPostPageResponse(
				items = page.values.map { view: SelfIntroPostView -> SelfIntroPostItemResponse.of(view) },
				receivedPendingChatRequestCount = page.receivedPendingChatRequestCount,
				sentPendingChatRequestCount = page.sentPendingChatRequestCount,
				hasNext = page.hasNext,
				nextCursor = page.nextCursor,
				companyVerified = page.companyVerified,
			)
	}
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.lounge.GetSelfIntroPostsE2ETest"`
Expected: PASS (기존 케이스 포함 전부 통과)

- [ ] **Step 7: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/ \
        oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/lounge/ \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/lounge/GetSelfIntroPostsE2ETest.kt
git commit -m "feat(lounge): 셀소 목록 응답에 회사 인증 여부 플래그 추가"
```

---

## Task 3: 미팅탭에 companyVerified 플래그

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/teammatch/query/dto/MeetingTab.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/teammatch/query/service/GetMeetingTabService.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/response/MeetingTabResponse.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/GetMeetingTabE2ETest.kt`

**Interfaces:**
- Consumes: `CheckCompanyVerifiedUseCase.isCompanyVerified(userId: Long): Boolean` (Task 1)
- Produces: `MeetingTab.companyVerified: Boolean`, 응답 필드 `data.companyVerified`

- [ ] **Step 1: 실패하는 E2E 테스트 두 개 작성**

이 스펙은 `UserEntity`를 만들지 않고 임의의 userId 리터럴을 그대로 쓴다(`persistMatchUser(9001L)` 식). 같은 스타일을 따른다.

`describe("GET /team-matches/v1/meeting-tab") { ... }` 블록 안 마지막에 다음 두 `context`를 추가한다:

```kotlin
		context("요청자 프로필에 회사명이 있으면") {
			it("companyVerified=true를 내려준다") {
				val userId = 9101L
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = userId, gender = Gender.MALE, companyName = "오늘소개"),
				)

				get("/team-matches/v1/meeting-tab") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.companyVerified", true)
				}
			}
		}

		context("요청자 프로필에 회사명이 없으면") {
			it("companyVerified=false를 내려준다") {
				val userId = 9102L
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = userId, gender = Gender.MALE),
				)

				get("/team-matches/v1/meeting-tab") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.companyVerified", false)
				}
			}
		}
```

이 파일은 `IntegrationUtil`·`UserDetailEntityFixture`·`Gender`·`get`·`expect`를 모두 임포트하고 있고 `afterTest`가 `QUserDetailEntity`를 정리하므로, **임포트도 정리 코드도 추가할 필요가 없다.**

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.GetMeetingTabE2ETest"`
Expected: FAIL — `data.companyVerified`가 null

- [ ] **Step 3: read model에 필드 추가**

`MeetingTab.kt`의 `data class MeetingTab`에 필드를 추가한다:

```kotlin
data class MeetingTab(
	val recommendedTeams: List<RecommendedTeam>,
	val receivedInvitationCount: Long,
	val myTeam: MyTeam?,
	/** 조회한 사용자가 회사 인증을 마쳤는지 여부. 프론트엔드가 미인증 사용자 화면을 분기하는 데 쓴다. */
	val companyVerified: Boolean,
)
```

KDoc의 항목 목록 마지막(`- [myTeam]: …` 줄 다음)에 한 줄 추가:

```
 * - [companyVerified]: 조회한 사용자의 회사 인증 완료 여부.
```

- [ ] **Step 4: 조회 서비스에서 플래그 채우기**

`GetMeetingTabService.kt`:

임포트 추가:

```kotlin
import com.org.oneulsogae.core.user.query.service.port.`in`.CheckCompanyVerifiedUseCase
```

생성자에 의존성 추가 — `private val timeGenerator: TimeGenerator,` 다음 줄:

```kotlin
	private val checkCompanyVerifiedUseCase: CheckCompanyVerifiedUseCase,
```

`get` 메서드 수정:

```kotlin
	override fun get(userId: Long): MeetingTab {
		val myTeam: MyTeam? = getMyTeamDao.findMyTeam(userId)
		return MeetingTab(
			// 결성(ACTIVE) 팀이 있으면 그 팀과 매칭된 상대 팀, 팀이 없거나 초대중(INVITING)이면 추천 팀을 같은 슬롯에 내려준다.
			recommendedTeams = teamCardsFor(userId, myTeam),
			receivedInvitationCount = getReceivedInvitationsDao.countInvited(userId),
			myTeam = myTeam,
			// 회사 인증 여부는 user 도메인 in-port로 읽는다. (미인증 사용자 화면 분기용 플래그)
			companyVerified = checkCompanyVerifiedUseCase.isCompanyVerified(userId),
		)
	}
```

- [ ] **Step 5: 응답 DTO에 필드 추가**

`MeetingTabResponse.kt`:

```kotlin
data class MeetingTabResponse(
	val recommendedTeams: List<RecommendedTeamResponse>,
	val receivedInvitationCount: Long,
	val myTeam: MyTeamResponse?,
	/** 요청한 사용자가 회사 인증을 마쳤는지 여부. 미인증이면 프론트엔드가 이용 제한 화면으로 분기한다. */
	val companyVerified: Boolean,
) {
```

그리고 `of`의 마지막 매핑(`myTeam = …` 블록) 다음에 한 줄 추가:

```kotlin
				companyVerified = meetingTab.companyVerified,
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.GetMeetingTabE2ETest"`
Expected: PASS

`MeetingTab(...)`을 생성하는 다른 곳이 없는지 확인:

Run: `grep -rn --include='*.kt' "MeetingTab(" . | grep -v "/build/"`
Expected: `MeetingTab.kt`(선언)와 `GetMeetingTabService.kt`(생성) 두 곳만

- [ ] **Step 7: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/teammatch/ \
        oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/response/MeetingTabResponse.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/GetMeetingTabE2ETest.kt
git commit -m "feat(teammatch): 미팅탭 응답에 회사 인증 여부 플래그 추가"
```

---

## Task 4: 소개 목록 응답을 객체로 래핑하고 companyVerified 추가

⚠️ **이 태스크는 `GET /matches/v1`의 응답 구조를 바꾼다** (배열 → 객체). 기존 E2E 세 개를 함께 고쳐야 한다.

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/solomatch/query/dto/MyMatches.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/solomatch/query/service/port/in/GetMatchesUseCase.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/solomatch/query/service/GetMatchesService.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/response/MatchResponse.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/SoloMatchController.kt:48-52`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/GetMatchesE2ETest.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/ExtraIntroIntegrationTest.kt:96-98`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/MatchUserSyncE2ETest.kt:154-156`

**Interfaces:**
- Consumes: `CheckCompanyVerifiedUseCase.isCompanyVerified(userId: Long): Boolean` (Task 1), 기존 `MatchesWithPartner(values).sortedForDisplay(): List<MatchWithPartner>`
- Produces:
  - `MyMatches(companyVerified: Boolean, matches: List<MatchWithPartner>)`
  - `GetMatchesUseCase.getMatches(userId: Long): MyMatches` (반환 타입 변경)
  - `MatchListResponse(companyVerified: Boolean, matches: List<MatchResponse>)` + `MatchListResponse.of(myMatches: MyMatches, today: LocalDate): MatchListResponse`
  - 응답: `{ "companyVerified": …, "matches": [ … ] }`

- [ ] **Step 1: 기존 E2E 테스트를 새 구조로 고치고, 플래그 케이스를 추가한다 (이 시점엔 실패)**

`GetMatchesE2ETest.kt`에서 `data.…` 경로를 `data.matches.…`로 바꾼다:

- `body("data.size()", 1)` → `body("data.matches.size()", 1)`
- `body("data[0].matchId", matchId.toInt())` → `body("data.matches[0].matchId", matchId.toInt())`
- `body("data[0].hasUserInterest", false)` → `body("data.matches[0].hasUserInterest", false)`
- `body("data[0].hasPartnerInterest", true)` → `body("data.matches[0].hasPartnerInterest", true)`
- `body("data[0].partner.userId", partnerUserId.toInt())` → `body("data.matches[0].partner.userId", partnerUserId.toInt())`
- `body("data[0].partner.nickname", "영희")` → `body("data.matches[0].partner.nickname", "영희")`
- `body("data[0].partner.traits", contains("요가", "등산"))` → `body("data.matches[0].partner.traits", contains("요가", "등산"))`
- `body("data[0].partner.interests", contains("재즈", "미술"))` → `body("data.matches[0].partner.interests", contains("재즈", "미술"))`
- `body("data[0].partner.lastLoginAt", notNullValue())` → `body("data.matches[0].partner.lastLoginAt", notNullValue())`
- `body("data.size()", 3)` → `body("data.matches.size()", 3)`
- `body("data.status", contains(...))` → `body("data.matches.status", contains(...))`
- `body("data.matchId", contains(...))` → `body("data.matches.matchId", contains(...))`

`ExtraIntroIntegrationTest.kt:96-98`도 같은 방식으로 바꾼다:

```kotlin
					body("data.matches.size()", 1)
					body("data.matches[0].matchId", matchId)
					body("data.matches[0].partner.userId", partnerUserId)
```

(`ExtraIntroIntegrationTest.kt:83`의 `body("data.matchId", greaterThan(0))`는 다른 엔드포인트(추가 소개 생성) 응답이므로 **바꾸지 않는다.** 해당 줄이 `get("/matches/v1")` 블록 안에 있는지 반드시 눈으로 확인할 것.)

`MatchUserSyncE2ETest.kt:154-156`:

```kotlin
					body("data.matches.size()", 1)
					body("data.matches[0].partner.userId", candidateUserId.toInt())
					body("data.matches[0].partner.nickname", "영희")
```

그리고 `GetMatchesE2ETest.kt`의 `describe("GET /matches/v1")` 블록 안 마지막에 플래그 케이스를 추가한다:

```kotlin
		context("요청자 프로필에 회사명이 있으면") {
			it("companyVerified=true를 내려준다") {
				val meUserId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "matches-verified", status = UserStatus.ACTIVE),
				).id!!
				IntegrationUtil.persist(
					UserDetailEntity(
						userId = meUserId,
						nickname = "인증회원",
						gender = Gender.MALE,
						birthday = LocalDate.of(1996, 1, 1),
						companyName = "오늘소개",
					),
				)

				get("/matches/v1") {
					bearer(accessTokenFor(meUserId))
				} expect {
					status(200)
					body("data.companyVerified", true)
					body("data.matches.size()", 0)
				}
			}
		}

		context("요청자 프로필에 회사명이 없으면") {
			it("companyVerified=false를 내려준다") {
				val meUserId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "matches-unverified", status = UserStatus.ACTIVE),
				).id!!
				IntegrationUtil.persist(
					UserDetailEntity(
						userId = meUserId,
						nickname = "미인증회원",
						gender = Gender.MALE,
						birthday = LocalDate.of(1996, 1, 1),
					),
				)

				get("/matches/v1") {
					bearer(accessTokenFor(meUserId))
				} expect {
					status(200)
					body("data.companyVerified", false)
					body("data.matches.size()", 0)
				}
			}
		}
```

(이 스펙은 픽스처 대신 `UserDetailEntity`를 직접 만드는 스타일이다. `UserDetailEntity`는 `companyName: String? = null` 파라미터를 갖고 있다. 필요한 임포트는 모두 이미 있다.)

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.GetMatchesE2ETest"`
Expected: FAIL — 응답이 아직 배열이라 `data.matches`가 null

- [ ] **Step 3: read model 생성**

`oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/solomatch/query/dto/MyMatches.kt`:

```kotlin
package com.org.oneulsogae.core.solomatch.query.dto

/**
 * 내 매칭 목록 화면의 조회 결과(read model).
 * 목록 자체([matches])와 함께 조회한 사용자의 회사 인증 여부([companyVerified])를 담는다.
 * (회사 인증을 마친 사용자만 소개 기능을 쓸 수 있어, 프론트엔드가 이 플래그로 화면을 분기한다)
 */
data class MyMatches(
	/** 조회한 사용자가 회사 인증을 마쳤는지 여부. */
	val companyVerified: Boolean,
	/** 노출 순서(상태 우선순위 → 최신순)로 정렬된 매칭 목록. 없으면 빈 리스트. */
	val matches: List<MatchWithPartner>,
)
```

- [ ] **Step 4: in-port 반환 타입 변경**

`GetMatchesUseCase.kt` 전체를 다음으로 바꾼다:

```kotlin
package com.org.oneulsogae.core.solomatch.query.service.port.`in`

import com.org.oneulsogae.core.solomatch.query.dto.MyMatches

/**
 * 내 매칭 목록 조회 인포트(유스케이스).
 * 해당 사용자가 참가한 모든 매칭(진행중/성사/거절)을 상대방 프로필과 함께 반환하고, 조회한 사용자의 회사 인증 여부를 같이 담는다.
 */
interface GetMatchesUseCase {

	fun getMatches(userId: Long): MyMatches
}
```

- [ ] **Step 5: 조회 서비스 수정**

`GetMatchesService.kt`:

임포트에서 사용하지 않게 되는 것은 없다(`MatchWithPartner`는 계속 쓴다). 다음 두 임포트를 추가한다:

```kotlin
import com.org.oneulsogae.core.solomatch.query.dto.MyMatches
import com.org.oneulsogae.core.user.query.service.port.`in`.CheckCompanyVerifiedUseCase
```

생성자에 의존성 추가 — `private val domainEventPublisher: DomainEventPublisher,` 다음 줄:

```kotlin
	private val checkCompanyVerifiedUseCase: CheckCompanyVerifiedUseCase,
```

메서드 시그니처와 마지막 return을 바꾼다:

```kotlin
	@Transactional(readOnly = true)
	override fun getMatches(userId: Long): MyMatches {
```

```kotlin
		// 노출 순서 규칙(상태 우선순위 → 최신순)은 일급 컬렉션이 캡슐화한다.
		// 회사 인증 여부는 user 도메인 in-port로 읽어 화면 분기용 플래그로 함께 내려준다.
		return MyMatches(
			companyVerified = checkCompanyVerifiedUseCase.isCompanyVerified(userId),
			matches = MatchesWithPartner(matches).sortedForDisplay(),
		)
	}
```

(중간의 성별 조회·이벤트 발행 로직은 그대로 둔다.)

- [ ] **Step 6: 응답 DTO 추가**

`MatchResponse.kt`의 `import` 아래, `data class MatchResponse` **위**에 다음을 추가한다:

```kotlin
/**
 * 내 매칭 목록 응답. 목록과 함께 요청한 사용자의 회사 인증 여부를 담는다.
 * 회사 인증을 마친 사용자만 소개 기능을 쓸 수 있어, 미인증이면 프론트엔드가 이용 제한 화면으로 분기한다.
 */
data class MatchListResponse(
	/** 요청한 사용자가 회사 인증을 마쳤는지 여부. */
	val companyVerified: Boolean,
	val matches: List<MatchResponse>,
) {
	companion object {

		fun of(myMatches: MyMatches, today: LocalDate): MatchListResponse =
			MatchListResponse(
				companyVerified = myMatches.companyVerified,
				matches = MatchResponse.listOf(myMatches.matches, today),
			)
	}
}
```

임포트 추가:

```kotlin
import com.org.oneulsogae.core.solomatch.query.dto.MyMatches
```

- [ ] **Step 7: 컨트롤러 수정**

`SoloMatchController.kt`의 `myMatches`를 다음으로 바꾼다:

```kotlin
	@Operation(summary = "내 매칭 목록 조회", description = "내 매칭 목록과 요청자의 회사 인증 여부를 반환한다.")
	@GetMapping
	fun myMatches(
		@LoginUser user: AuthUser,
	): ApiResponse<MatchListResponse> =
		ApiResponse.success(MatchListResponse.of(getMatchesUseCase.getMatches(user.id), timeGenerator.today()))
```

임포트에서 `MatchResponse`가 이 파일에서 더 이상 직접 쓰이지 않으면 제거하고, `MatchListResponse` 임포트를 추가한다:

```kotlin
import com.org.oneulsogae.api.match.response.MatchListResponse
```

- [ ] **Step 8: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.GetMatchesE2ETest" --tests "com.org.oneulsogae.api.match.ExtraIntroIntegrationTest" --tests "com.org.oneulsogae.api.match.MatchUserSyncE2ETest" --tests "com.org.oneulsogae.api.match.MatchCheckedOnViewE2ETest"`
Expected: PASS (4개 스펙 전부)

- [ ] **Step 9: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/solomatch/query/ \
        oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/response/MatchResponse.kt \
        oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/SoloMatchController.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/
git commit -m "feat(match): 소개 목록 응답을 객체로 감싸고 회사 인증 여부 플래그 추가"
```

---

## Task 5: 소개 관심요청에 회사 인증 검증

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/solomatch/command/application/SendInterestService.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/SendInterestE2ETest.kt`

**Interfaces:**
- Consumes: `CheckCompanyVerifiedUseCase.validateCompanyVerified(userId: Long)` (Task 1)
- Produces: `POST /matches/v1/{matchId}/interest`가 미인증 요청에 403 `USER-035` 반환

- [ ] **Step 1: 기존 케이스에 회사명을 채우고, 실패하는 새 케이스를 추가한다**

⚠️ 이 스펙의 기존 케이스들은 요청자 프로필에 회사명이 없거나 프로필 자체가 없다. 검증을 추가하면 전부 403으로 깨지므로 **먼저 고친다.**

**(a) `context("상대가 아직 관심을 안 보낸 매칭에 관심을 보내면")` — 요청자(1001L) 프로필에 회사명 추가**

```kotlin
				// 보낸 사람(남성) 프로필 — 알람 문구에 닉네임이 들어간다. 회사 인증을 마친 사용자만 관심을 보낼 수 있어 회사명도 채운다.
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = maleUserId, gender = Gender.MALE, nickname = "철수", companyName = "오늘소개"),
				)
```

**(b) `context("상대가 이미 관심을 보낸 매칭에 관심을 보내면")` — 요청자(1002L) 프로필에 회사명 추가**

```kotlin
				// 두 사람 프로필 — 성사 알람 문구에 각자 상대의 닉네임이 들어간다. 요청자는 회사 인증을 마쳤어야 관심을 보낼 수 있다.
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = maleUserId, gender = Gender.MALE, nickname = "철수", companyName = "오늘소개"),
				)
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = femaleUserId, gender = Gender.FEMALE, nickname = "영희"),
				)
```

**(c) `context("코인 잔액이 부족하면")` — 요청자(1003L) 프로필이 아예 없으므로 신규 추가**

`val match: SoloMatchEntity = persistMatch(maleUserId, femaleUserId)` 다음 줄에 삽입한다:

```kotlin
				// 회사 인증을 마쳐야 코인 잔액 검증까지 도달한다.
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = maleUserId, gender = Gender.MALE, companyName = "오늘소개"),
				)
```

**(d) 새 케이스 추가** — `context("인증 토큰이 없으면")` **앞**에 삽입한다:

```kotlin
		context("요청자가 회사 인증을 마치지 않았으면") {
			it("403(USER-035)을 반환하고 코인이 차감되지 않는다") {
				val maleUserId = 1004L
				val femaleUserId = 2004L
				val match: SoloMatchEntity = persistMatch(maleUserId, femaleUserId)
				// 회사명이 없는 프로필 = 회사 인증 미완료
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = maleUserId, gender = Gender.MALE),
				)
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = maleUserId, balance = 100))

				post("/matches/v1/${match.id}/interest") {
					bearer(accessTokenFor(maleUserId))
				} expect {
					status(403)
					body("success", false)
					body("error.code", "USER-035")
				}

				// 차단이 코인 차감보다 앞이라 잔액과 매칭 상태가 그대로다.
				coinBalanceOf(maleUserId) shouldBe 100
				matchStatusOf(match.id!!) shouldBe MatchStatus.PROPOSED
			}
		}
```

이 파일은 필요한 임포트(`UserDetailEntityFixture`, `CoinBalanceEntityFixture`, `IntegrationUtil`, `shouldBe`, `MatchStatus`, `Gender`)를 모두 갖고 있으므로 **임포트 추가는 필요 없다.**

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.SendInterestE2ETest"`
Expected: FAIL — 새 케이스가 403 대신 200을 받음

- [ ] **Step 3: 서비스에 검증 추가**

`SendInterestService.kt`:

임포트 추가:

```kotlin
import com.org.oneulsogae.core.user.query.service.port.`in`.CheckCompanyVerifiedUseCase
```

생성자에 의존성 추가 — `private val domainEventPublisher: DomainEventPublisher,` 다음 줄:

```kotlin
	private val checkCompanyVerifiedUseCase: CheckCompanyVerifiedUseCase,
```

`sendInterest` 본문 수정:

```kotlin
	@DistributedLock(prefix = LockKeyConstraints.MATCH_INTEREST, keys = ["#matchId"], waitTime = 0)
	@Transactional
	override fun sendInterest(userId: Long, matchId: Long): Match {
		// 회사 인증을 마친 사용자만 소개 기능을 쓸 수 있다. 코인 차감 전에 막아 미인증 요청에 과금이 생기지 않게 한다.
		checkCompanyVerifiedUseCase.validateCompanyVerified(userId)

		// 대상 매칭을 조회한다. 없으면 예외.
		val match: Match = getMatchPort.findById(matchId)
			?: throw BusinessException(MatchErrorCode.MATCH_NOT_FOUND)
		match.validateRespondable(userId)
```

클래스 KDoc의 "다른 도메인(coin/chat)은…" 줄 다음에 한 문장 추가:

```
 * 회사 인증 여부는 user 도메인 in-port([CheckCompanyVerifiedUseCase])로 검증한다. (미인증이면 코인 차감 전에 403으로 막는다)
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.SendInterestE2ETest"`
Expected: PASS (기존 케이스 포함 전부)

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/solomatch/command/application/SendInterestService.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/SendInterestE2ETest.kt
git commit -m "feat(match): 소개 관심요청에 회사 인증 검증 추가"
```

---

## Task 6: 미팅 관심요청에 회사 인증 검증

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/teammatch/command/application/SendTeamInterestService.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/SendTeamInterestE2ETest.kt`

**Interfaces:**
- Consumes: `CheckCompanyVerifiedUseCase.validateCompanyVerified(userId: Long)` (Task 1)
- Produces: `POST /team-matches/v1/{teamMatchId}/interest`가 미인증 요청에 403 `USER-035` 반환

- [ ] **Step 1: 기존 케이스에 회사명을 채우고, 실패하는 새 케이스를 추가한다**

⚠️ 이 스펙은 `MatchUserEntity`만 시딩하고 `UserDetailEntity`는 **전혀 만들지 않는다.** 검증을 추가하면 모든 케이스가 403으로 깨지므로 **요청자마다 회사명 있는 프로필을 만들어 준다.**

**(a) 요청자 프로필 헬퍼를 추가한다** — `persistMatchUser` 함수 다음에 삽입:

```kotlin
	// 회사 인증을 마친(회사명이 채워진) 프로필. 관심 보내기는 회사 인증을 마친 사용자만 할 수 있다.
	fun persistVerifiedDetail(userId: Long) {
		IntegrationUtil.persist(
			UserDetailEntityFixture.create(userId = userId, gender = Gender.MALE, companyName = "오늘소개"),
		)
	}
```

**(b) 관심을 보내는 요청자마다 헬퍼를 호출한다.** 아래 네 곳에서 `post("/team-matches/…/interest")` 호출 **앞**에 한 줄씩 넣는다:

| context | 삽입할 줄 |
|---|---|
| `상대 팀이 아직 신청 안 한 팀 매칭에 관심을 보내면` | `persistVerifiedDetail(myOwner)` |
| `상대 팀이 이미 신청한 팀 매칭에 관심을 보내면` | `persistVerifiedDetail(myOwner)` |
| `코인 잔액이 부족하면` | `persistVerifiedDetail(myOwner)` |
| `참가 팀 구성원이 아닌 사용자가 관심을 보내면` | `persistVerifiedDetail(outsider)` |

(마지막 케이스는 `NOT_TEAM_MATCH_PARTICIPANT`를 기대하므로, 회사 인증 검증을 먼저 통과해야 원래 기대값에 도달한다.)

**(c) `afterTest`에 프로필 정리를 추가한다** — `IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)` 다음 줄:

```kotlin
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
```

**(d) 임포트를 추가한다** (기존 임포트 목록에 없는 것만):

```kotlin
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
```

(`IntegrationUtil`·`Gender`는 이미 임포트되어 있다.)

**(e) 새 케이스 추가** — `context("인증 토큰이 없으면")` **앞**에 삽입:

```kotlin
		context("요청자가 회사 인증을 마치지 않았으면") {
			it("403(USER-035)을 반환하고 코인이 차감되지 않는다") {
				val myOwner = 6301L
				val myInvited = 6302L
				val oppOwner = 6303L
				val oppInvited = 6304L
				val myTeamId: Long = formedTeam(myOwner, myInvited)
				val opponentTeamId: Long = formedTeam(oppOwner, oppInvited)
				val teamMatchId: Long = persistTeamMatch(myTeamId, opponentTeamId)
				// 회사명이 없는 프로필 = 회사 인증 미완료
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = myOwner, gender = Gender.MALE),
				)
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = myOwner, balance = 100))

				post("/team-matches/v1/$teamMatchId/interest") {
					bearer(accessTokenFor(myOwner))
				} expect {
					status(403)
					body("success", false)
					body("error.code", "USER-035")
				}

				// 차단이 코인 차감보다 앞이라 잔액이 그대로다.
				coinBalanceOf(myOwner) shouldBe 100
			}
		}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.SendTeamInterestE2ETest"`
Expected: FAIL — 새 케이스가 403 대신 200을 받음

- [ ] **Step 3: 서비스에 검증 추가**

`SendTeamInterestService.kt`:

임포트 추가:

```kotlin
import com.org.oneulsogae.core.user.query.service.port.`in`.CheckCompanyVerifiedUseCase
```

생성자에 의존성 추가 — `private val domainEventPublisher: DomainEventPublisher,` 다음 줄:

```kotlin
	private val checkCompanyVerifiedUseCase: CheckCompanyVerifiedUseCase,
```

`sendInterest` 본문 수정:

```kotlin
	@DistributedLock(prefix = LockKeyConstraints.TEAM_MATCH_INTEREST, keys = ["#teamMatchId"], waitTime = 0)
	@Transactional
	override fun sendInterest(userId: Long, teamMatchId: Long): TeamMatch {
		// 회사 인증을 마친 사용자만 미팅 기능을 쓸 수 있다. 코인 차감 전에 막아 미인증 요청에 과금이 생기지 않게 한다.
		checkCompanyVerifiedUseCase.validateCompanyVerified(userId)

		val teamMatch: TeamMatch = getTeamMatchPort.findById(teamMatchId)
			?: throw BusinessException(TeamMatchErrorCode.TEAM_MATCH_NOT_FOUND)
```

클래스 KDoc의 "다른 도메인(coin/chat)은…" 줄 다음에 한 문장 추가:

```
 * 회사 인증 여부는 user 도메인 in-port([CheckCompanyVerifiedUseCase])로 검증한다. (미인증이면 코인 차감 전에 403으로 막는다)
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.SendTeamInterestE2ETest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/teammatch/command/application/SendTeamInterestService.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/SendTeamInterestE2ETest.kt
git commit -m "feat(teammatch): 미팅 관심요청에 회사 인증 검증 추가"
```

---

## Task 7: 라운지 대화신청에 회사 인증 검증

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/command/application/RequestLoungeChatService.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/lounge/RequestLoungeChatE2ETest.kt`

**Interfaces:**
- Consumes: `CheckCompanyVerifiedUseCase.validateCompanyVerified(userId: Long)` (Task 1)
- Produces: `POST /lounge/v1/self-intro-posts/{postId}/chat-requests`가 미인증 요청에 403 `USER-035` 반환

- [ ] **Step 1: 기존 케이스에 회사명을 채우고, 실패하는 새 케이스를 추가한다**

⚠️ 이 스펙의 기존 케이스들은 신청자 프로필에 회사명이 없거나 프로필 자체가 없다. 검증이 **가장 앞**에 들어가므로 7개 케이스 전부가 403으로 깨진다. **먼저 고친다.** (작성자(author) 프로필의 회사명은 무관하므로 건드리지 않는다)

**(a) 신청자 프로필에 `companyName = "오늘소개"`를 추가한다** — 아래 다섯 곳:

| context | 고칠 줄 |
|---|---|
| `다른 사람의 셀소에 코인을 갖고 신청하면` | `UserDetailEntityFixture.create(userId = requesterId, gender = Gender.MALE)` |
| `같은 글에 두 번 신청하면` | `UserDetailEntityFixture.create(userId = requesterId, gender = Gender.MALE)` |
| `코인이 부족하면` | `UserDetailEntityFixture.create(userId = requesterId, gender = Gender.MALE)` |
| `성별이 같은 상대의 셀소에 신청하면` | `UserDetailEntityFixture.create(userId = requesterId, gender = Gender.MALE)` |
| `프로필이 없어 성별을 확인할 수 없으면` | `UserDetailEntityFixture.create(userId = requesterId, gender = Gender.MALE)` |

각각 다음으로 바꾼다:

```kotlin
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = requesterId, gender = Gender.MALE, companyName = "오늘소개"))
```

**(b) 신청자 프로필이 아예 없는 두 케이스에 프로필을 신규 추가한다**

`context("본인이 쓴 셀소에 신청하면")` — 여기서는 `authorId`가 신청자다. `CoinBalanceEntityFixture` persist 앞에 삽입:

```kotlin
				// 회사 인증을 마쳐야 본인 글 차단(LOUNGE-009)까지 도달한다.
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = authorId, gender = Gender.MALE, companyName = "오늘소개"))
```

`context("없는 글에 신청하면")` — `CoinBalanceEntityFixture` persist 앞에 삽입:

```kotlin
				// 회사 인증을 마쳐야 글 존재 확인(LOUNGE-008)까지 도달한다.
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = requesterId, gender = Gender.MALE, companyName = "오늘소개"))
```

**(c) 새 케이스 추가** — `describe` 블록 안 마지막(`context("없는 글에 신청하면")` 다음)에 삽입:

```kotlin
		context("신청자가 회사 인증을 마치지 않았으면") {
			it("403(USER-035)을 반환하고 코인이 차감되지 않는다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-author-8")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-user-8")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = authorId, gender = Gender.FEMALE))
				// 회사명이 없는 프로필 = 회사 인증 미완료
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = requesterId, gender = Gender.MALE))
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = requesterId, balance = 100))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(requesterId)}")
					.post("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(403)
					.body("error.code", Matchers.equalTo("USER-035"))

				// 차단이 코인 차감보다 앞이라 잔액이 그대로다.
				val balance: Int = IntegrationUtil.getQuery()
					.select(QCoinBalanceEntity.coinBalanceEntity.balance)
					.from(QCoinBalanceEntity.coinBalanceEntity)
					.where(QCoinBalanceEntity.coinBalanceEntity.userId.eq(requesterId))
					.fetchFirst()!!
				balance shouldBe 100
			}
		}
```

이 파일은 필요한 임포트를 모두 갖고 있으므로 **임포트 추가는 필요 없다.**

**(d) `afterTest`에 프로필 정리를 추가한다** — 이 스펙은 `UserDetailEntity`를 정리하지 않는다. `QUserDetailEntity`가 이미 임포트되어 있으니 `afterTest` 마지막에 한 줄 추가:

```kotlin
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.lounge.RequestLoungeChatE2ETest"`
Expected: FAIL — 새 케이스가 403 대신 200을 받음

- [ ] **Step 3: 서비스에 검증 추가**

`RequestLoungeChatService.kt`:

임포트 추가:

```kotlin
import com.org.oneulsogae.core.user.query.service.port.`in`.CheckCompanyVerifiedUseCase
```

생성자에 의존성 추가 — `private val getUserDetailUseCase: GetUserDetailUseCase,` 다음 줄:

```kotlin
	private val checkCompanyVerifiedUseCase: CheckCompanyVerifiedUseCase,
```

`request` 본문 수정 — 글 존재 확인 다음, 중복 확인 앞에 넣는다:

```kotlin
	@DistributedLock(prefix = LockKeyConstraints.LOUNGE_CHAT_REQUEST, keys = ["#postId", "#userId"], waitTime = 0)
	@Transactional
	override fun request(userId: Long, postId: Long): RequestLoungeChatResult {
		// 회사 인증을 마친 사용자만 라운지 대화신청을 할 수 있다. 코인 차감 전에 막아 미인증 요청에 과금이 생기지 않게 한다.
		checkCompanyVerifiedUseCase.validateCompanyVerified(userId)

		val post: LoungePost = getLoungePostPort.findById(postId)
			?: throw BusinessException(LoungeErrorCode.SELF_INTRO_POST_NOT_FOUND, "셀소를 찾을 수 없습니다: $postId")
```

클래스 KDoc의 "성별은 user 도메인 in-port…" 줄 다음에 한 문장 추가:

```
 * 회사 인증 여부도 user 도메인 in-port([CheckCompanyVerifiedUseCase])로 검증한다. (미인증이면 코인 차감 전에 403으로 막는다)
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.lounge.RequestLoungeChatE2ETest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/command/application/RequestLoungeChatService.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/lounge/RequestLoungeChatE2ETest.kt
git commit -m "feat(lounge): 셀소 대화신청에 회사 인증 검증 추가"
```

---

## Task 8: 전체 검증

**Files:** 없음 (검증만)

- [ ] **Step 1: 전체 테스트 실행**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`

실패가 나오면, 실패한 스펙이 (a) 소개 목록 응답 구조 변경 때문인지 → `data.` → `data.matches.` 로 고친다, (b) 관심요청 403 때문인지 → 해당 스펙 요청자 프로필에 `companyName`을 채운다, 를 판단해 고친다. **테스트를 통과시키려고 프로덕션 검증을 약화시키지 않는다.**

- [ ] **Step 2: 판정 로직이 한 곳에만 있는지 확인**

Run: `grep -rn --include='*.kt' -e "companyName != null" -e "companyName == null" oneulsogae-core/src/main oneulsogae-api/src/main`
Expected: `CheckCompanyVerifiedService.kt` 한 곳만

- [ ] **Step 3: 미커밋 변경이 없는지 확인**

Run: `git status --short`
Expected: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/chat/ChatRoomTest.kt`와 `oneulsogae-core/.../chat/command/domain/ChatRoom.kt` **두 개만** 남아 있어야 한다. (이 두 파일은 이번 작업과 무관한 이전 세션의 변경이므로 **건드리지도, 커밋하지도 않는다.**)

- [ ] **Step 4: 프론트엔드 대응 사항 안내**

사용자에게 다음을 그대로 전달한다 (프론트엔드 코드는 수정하지 않는다):

```
[프론트엔드 대응 필요]

1. GET /matches/v1 — 응답 구조 변경 (breaking)
   변경 전: data = [ {matchId, status, partner, …}, … ]
   변경 후: data = { companyVerified: Boolean, matches: [ {matchId, status, partner, …}, … ] }
   → 소개 목록 응답 DTO와 파싱부를 `data.matches`로 바꿔야 합니다.

2. GET /team-matches/v1/meeting-tab — 필드 추가 (호환)
   data.companyVerified: Boolean 추가. 기존 파싱은 그대로 동작합니다.

3. GET /lounge/v1/self-intro-posts — 필드 추가 (호환)
   data.companyVerified: Boolean 추가. 기존 파싱은 그대로 동작합니다.

4. 관심요청 3개 API가 미인증 사용자에게 403 / error.code = "USER-035"를 반환합니다.
   - POST /matches/v1/{matchId}/interest
   - POST /team-matches/v1/{teamMatchId}/interest
   - POST /lounge/v1/self-intro-posts/{postId}/chat-requests
   → 이 코드를 받으면 회사 인증 유도 화면으로 보내주세요.
```
