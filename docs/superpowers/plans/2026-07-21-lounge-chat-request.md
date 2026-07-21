# 라운지 셀소 대화 신청 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 라운지 셀소 상세에서 코인을 내고 작성자에게 대화를 신청하고, 작성자가 신청자 중 원하는 상대를 골라 수락하면 채팅방이 열리는 API를 만든다.

**Architecture:** 헥사고날 + CQRS. lounge 도메인에 `LoungeChatRequest` 애그리거트를 새로 두고, command(신청·수락)와 query(신청 목록)를 분리한다. 코인 차감은 coin 도메인 in-port(`SpendCoinUseCase`), 채팅방 생성은 chat 도메인 in-port(`SaveChatRoomUseCase`)에 위임한다. 채팅방은 기존 `chat_rooms`의 다형성 참조에 `ChatRoomMatchType.LOUNGE`를 추가해 붙이므로 채팅 스키마 변경은 없다.

**Tech Stack:** Kotlin 2.2.21 / JVM 21, Spring Boot 4.0.6, Spring Data JPA, QueryDSL, MySQL, Redisson 분산 락, Kotest + RestAssured + Testcontainers

**설계 문서:** `docs/superpowers/specs/2026-07-21-lounge-chat-request-design.md`

## Global Constraints

- **응답은 항상 한국어로 한다.** 코드 주석·KDoc도 한국어.
- **탭 인덴트**를 쓴다. 기존 파일 스타일을 그대로 따른다.
- **타입 명시**: 변수·반환 타입·람다 파라미터 타입을 생략하지 않는다.
- **`LocalDateTime.now()` 직접 호출 금지.** `TimeGenerator`를 주입받는다.
- **Controller는 Service가 아니라 in-port `UseCase` 인터페이스를 주입**한다.
- **다른 도메인은 in-port(`UseCase`)로만 참조**한다. 다른 도메인의 out-port·Service 구현체를 직접 주입하지 않는다.
- **query 패키지는 자기 dao에만 의존**한다. command 도메인·포트를 참조하지 않는다.
- 코인 차감액은 서버가 `CoinUsageType.coinAmount`로 산출한다. 클라이언트가 금액을 보내지 않는다.
- **`oneulsogae-backend`만 수정한다.** `meeple-frontend`는 건드리지 않는다.
- 커밋 메시지 형식: `<type>(lounge): <설명>`

## File Structure

**신규 파일**

| 경로 | 책임 |
|---|---|
| `oneulsogae-common/.../common/lounge/LoungeChatRequestStatus.kt` | 신청 상태 enum (core·infra 공유) |
| `oneulsogae-core/.../lounge/command/domain/LoungeChatRequest.kt` | 신청 도메인 모델 + 생성·수락 규칙 |
| `oneulsogae-core/.../lounge/command/domain/event/LoungeChatRequested.kt` | 신청 도메인 이벤트 |
| `oneulsogae-core/.../lounge/command/domain/event/LoungeChatRequestAccepted.kt` | 수락 도메인 이벤트 |
| `oneulsogae-core/.../lounge/command/application/RequestLoungeChatService.kt` | 신청 유스케이스 구현 |
| `oneulsogae-core/.../lounge/command/application/AcceptLoungeChatService.kt` | 수락 유스케이스 구현 |
| `oneulsogae-core/.../lounge/command/application/LoungeEventHandler.kt` | 커밋 후 알람 저장 |
| `oneulsogae-core/.../lounge/command/application/port/in/RequestLoungeChatUseCase.kt` | 신청 in-port |
| `oneulsogae-core/.../lounge/command/application/port/in/AcceptLoungeChatUseCase.kt` | 수락 in-port |
| `oneulsogae-core/.../lounge/command/application/port/in/result/RequestLoungeChatResult.kt` | 신청 결과 |
| `oneulsogae-core/.../lounge/command/application/port/in/result/AcceptLoungeChatResult.kt` | 수락 결과 |
| `oneulsogae-core/.../lounge/command/application/port/out/GetLoungePostPort.kt` | 글 단건 조회 out-port |
| `oneulsogae-core/.../lounge/command/application/port/out/GetLoungeChatRequestPort.kt` | 신청 조회 out-port |
| `oneulsogae-core/.../lounge/command/application/port/out/SaveLoungeChatRequestPort.kt` | 신청 저장 out-port |
| `oneulsogae-core/.../lounge/query/dao/GetLoungeChatRequestDao.kt` | 신청 목록 조회 dao |
| `oneulsogae-core/.../lounge/query/dto/LoungeChatRequestView.kt` | 신청 목록 read model |
| `oneulsogae-core/.../lounge/query/dto/LoungeChatRequestPage.kt` | 커서 페이지 일급 컬렉션 |
| `oneulsogae-core/.../lounge/query/service/GetLoungeChatRequestsService.kt` | 신청 목록 조회 서비스 |
| `oneulsogae-core/.../lounge/query/service/port/in/GetLoungeChatRequestsUseCase.kt` | 목록 조회 in-port |
| `oneulsogae-infra/.../lounge/command/entity/LoungeChatRequestEntity.kt` | 신청 영속성 엔티티 |
| `oneulsogae-infra/.../lounge/command/mapper/LoungeChatRequestMapper.kt` | 엔티티 ↔ 도메인 변환 |
| `oneulsogae-infra/.../lounge/command/repository/LoungeChatRequestJpaRepository.kt` | Spring Data 리포지토리 |
| `oneulsogae-infra/.../lounge/command/adapter/LoungeChatRequestAdapter.kt` | Get/Save 신청 out-port 구현 |
| `oneulsogae-infra/.../lounge/query/GetLoungeChatRequestDaoImpl.kt` | QueryDSL 목록 조회 구현 |
| `oneulsogae-infra/src/testFixtures/.../fixture/LoungeChatRequestEntityFixture.kt` | E2E 픽스처 |
| `oneulsogae-api/.../api/lounge/LoungeChatRequestController.kt` | HTTP 경계 3개 엔드포인트 |
| `oneulsogae-api/.../api/lounge/response/LoungeChatRequestResponse.kt` | 신청 결과 응답 |
| `oneulsogae-api/.../api/lounge/response/LoungeChatRequestPageResponse.kt` | 목록 응답 |
| `oneulsogae-api/.../api/lounge/response/AcceptLoungeChatResponse.kt` | 수락 결과 응답 |
| `docs/migration/lounge_chat_requests.sql` | 실DB DDL |

**수정 파일**

| 경로 | 변경 |
|---|---|
| `oneulsogae-common/.../common/coin/CoinUsageType.kt` | `LOUNGE_CHAT_INIT`·`LOUNGE_CHAT_ACCEPT` 추가 |
| `oneulsogae-common/.../common/chat/ChatRoomMatchType.kt` | `LOUNGE` 추가 |
| `oneulsogae-common/.../common/alarm/AlarmType.kt` | 알람 2종 + `category()` 분기 추가 |
| `oneulsogae-core/.../lounge/LoungeErrorCode.kt` | 에러 코드 5개 추가 |
| `oneulsogae-core/.../common/lock/LockKeyConstraints.kt` | 락 접두사 2개 추가 |
| `oneulsogae-core/.../chat/command/application/DeactivateChatRoomMemberService.kt` | `when`에 `LOUNGE` 분기 |
| `oneulsogae-core/.../report/command/application/CreateReportService.kt` | `when`에 `LOUNGE` 분기 |
| `oneulsogae-infra/.../lounge/command/adapter/LoungePostAdapter.kt` | `GetLoungePostPort` 구현 추가 |
| `oneulsogae-infra/.../lounge/command/repository/LoungePostJpaRepository.kt` | 변경 없음 (`findById` 상속 사용) |

패키지 접두사: core는 `com.org.oneulsogae.core`, infra는 `com.org.oneulsogae.infra`, api는 `com.org.oneulsogae.api`, common은 `com.org.oneulsogae.common`.

---

## Task 1: 공통 enum·에러 코드·락 상수 추가

이후 모든 태스크가 참조하는 상수 기반을 먼저 깔고, `ChatRoomMatchType`에 값을 추가하면서 깨지는 `when` 두 곳을 함께 고친다.

**Files:**
- Create: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/lounge/LoungeChatRequestStatus.kt`
- Modify: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/coin/CoinUsageType.kt`
- Modify: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/chat/ChatRoomMatchType.kt`
- Modify: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/alarm/AlarmType.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/LoungeErrorCode.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/common/lock/LockKeyConstraints.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/chat/command/application/DeactivateChatRoomMemberService.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/report/command/application/CreateReportService.kt`

**Interfaces:**
- Consumes: 없음 (첫 태스크)
- Produces:
  - `LoungeChatRequestStatus.PENDING`, `LoungeChatRequestStatus.ACCEPTED`
  - `CoinUsageType.LOUNGE_CHAT_INIT` (coinAmount=32), `CoinUsageType.LOUNGE_CHAT_ACCEPT` (coinAmount=32)
  - `ChatRoomMatchType.LOUNGE`
  - `AlarmType.LOUNGE_CHAT_REQUEST_RECEIVED`, `AlarmType.LOUNGE_CHAT_ACCEPTED`
  - `LoungeErrorCode.LOUNGE_CHAT_REQUEST_SELF` / `LOUNGE_CHAT_REQUEST_DUPLICATED` / `LOUNGE_POST_NOT_OWNED` / `LOUNGE_CHAT_REQUEST_NOT_FOUND` / `LOUNGE_CHAT_REQUEST_ALREADY_ACCEPTED`
  - `LockKeyConstraints.LOUNGE_CHAT_REQUEST`, `LockKeyConstraints.LOUNGE_CHAT_ACCEPT`

- [ ] **Step 1: 신청 상태 enum 생성**

`oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/lounge/LoungeChatRequestStatus.kt`:

```kotlin
package com.org.oneulsogae.common.lounge

/**
 * 라운지 셀소 대화 신청의 상태.
 * 신청은 [PENDING]으로 시작하고, 글 작성자가 수락하면 [ACCEPTED]가 되며 그때 채팅방이 생성된다.
 * 거절·만료는 두지 않는다(수락되지 않은 신청은 PENDING으로 남는다).
 */
enum class LoungeChatRequestStatus(val description: String) {

	/** 신청됨. 글 작성자가 아직 수락하지 않은 상태. */
	PENDING("신청됨"),

	/** 수락됨. 채팅방이 생성된 상태. */
	ACCEPTED("수락됨"),
}
```

- [ ] **Step 2: `CoinUsageType`에 라운지 대화 신청·수락 추가**

`CoinUsageType.kt`의 `EXTRA_INTRO` 항목 **아래**에 추가한다 (마지막 항목 뒤 `;`는 없으므로 콤마로 이어 붙인다):

```kotlin
	/** 추가 소개(오늘의 추천 외 1명 더 소개받기). */
	EXTRA_INTRO("추가 소개", 30),

	/** 라운지 셀소 대화 신청. */
	LOUNGE_CHAT_INIT("라운지 대화 신청", 32),

	/** 라운지 셀소 대화 수락. */
	LOUNGE_CHAT_ACCEPT("라운지 대화 수락", 32),
}
```

- [ ] **Step 3: `ChatRoomMatchType`에 `LOUNGE` 추가**

`ChatRoomMatchType.kt`의 `TEAM` 아래에 추가한다:

```kotlin
	/** 2:2(팀) 매칭에서 생성된 채팅방. (team_matches.id) */
	TEAM,

	/** 라운지 셀소 대화 신청 수락으로 생성된 채팅방. (lounge_chat_requests.id) */
	LOUNGE,
}
```

- [ ] **Step 4: 컴파일해서 `when` 두 곳이 깨지는지 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: FAIL — `DeactivateChatRoomMemberService.kt`와 `CreateReportService.kt`에서
`'when' expression must be exhaustive, add necessary 'LOUNGE' branch or 'else' branch instead`

- [ ] **Step 5: `DeactivateChatRoomMemberService`에 `LOUNGE` 분기 추가**

`leftMessageOf`를 다음으로 바꾼다 (라운지 방도 1:1 사용자 방이라 SOLO와 같은 문구를 쓴다):

```kotlin
	// 채팅방 종류에 맞는 나감 안내 문구를 고른다. (팀 해체 vs 1:1 매칭 종료)
	// 라운지 대화 방은 1:1 사용자 방이므로 SOLO와 같은 문구를 쓴다.
	private fun leftMessageOf(matchType: ChatRoomMatchType): String =
		when (matchType) {
			ChatRoomMatchType.TEAM -> TEAM_LEFT_MESSAGE
			ChatRoomMatchType.SOLO, ChatRoomMatchType.LOUNGE -> SOLO_LEFT_MESSAGE
		}
```

- [ ] **Step 6: `CreateReportService`에 `LOUNGE` 분기 추가**

`targetType` 계산부를 다음으로 바꾼다:

```kotlin
		// 라운지 대화 방은 1:1 사용자 방이므로 신고 대상도 상대 유저다.
		val targetType: ReportTargetType = when (chatRoomMatch.matchType) {
			ChatRoomMatchType.SOLO, ChatRoomMatchType.LOUNGE -> ReportTargetType.USER
			ChatRoomMatchType.TEAM -> ReportTargetType.TEAM
		}
```

- [ ] **Step 7: `AlarmType`에 라운지 알람 2종 추가**

`COIN_DAILY_ACQUIRED` 아래(`;` 앞)에 추가한다:

```kotlin
	/** [코인] 출석(DAILY) 코인이 적립됨. (본인에게, 인앱 전용 — 알림톡 push 없음) */
	COIN_DAILY_ACQUIRED("코인 적립"),

	/** [라운지] 내 셀소에 대화 신청이 들어옴. (글 작성자에게) */
	LOUNGE_CHAT_REQUEST_RECEIVED("대화 신청 받음"),

	/** [라운지] 내가 보낸 대화 신청이 수락됨. (신청자에게) */
	LOUNGE_CHAT_ACCEPTED("대화 신청 수락됨"),
	;
```

그리고 `category()`의 `ONE_TO_ONE` 분기에 두 값을 덧붙인다. 라운지 대화도 1:1 소개 성격이라 새 카테고리를 만들지 않고 기존 토글이 관장한다:

```kotlin
	/** 이 알람 유형이 속한 알림 설정 카테고리. (알림톡 전송 게이트가 이 값으로 사용자 설정을 평가) */
	fun category(): NotificationCategory =
		when (this) {
			ONE_TO_ONE_INTEREST_RECEIVED, ONE_TO_ONE_MATCH_CHECKED, ONE_TO_ONE_MATCHED, ONE_TO_ONE_MATCH_ENDED, ONE_TO_ONE_NO_MATCH_TODAY,
			// 라운지 대화 신청도 1:1 소개 성격이라 별도 카테고리를 두지 않고 ONE_TO_ONE 토글이 관장한다.
			LOUNGE_CHAT_REQUEST_RECEIVED, LOUNGE_CHAT_ACCEPTED ->
				NotificationCategory.ONE_TO_ONE
			MANY_TO_MANY_INTEREST_RECEIVED, MANY_TO_MANY_MATCHED, MANY_TO_MANY_MATCH_ENDED, MANY_TO_MANY_NO_MATCH_TODAY ->
				NotificationCategory.MEETING
			TEAM_INVITATION_RECEIVED, TEAM_INVITATION_DECLINED, TEAM_INVITATION_CANCELED, TEAM_INVITATION_ACCEPTED, TEAM_DISBANDED ->
				NotificationCategory.TEAM
			COIN_DAILY_ACQUIRED ->
				NotificationCategory.COIN
		}
```

- [ ] **Step 8: `LoungeErrorCode`에 에러 코드 5개 추가**

`SELF_INTRO_POST_NOT_FOUND` 아래에 새 섹션으로 추가한다:

```kotlin
	/** 셀소를 id로 찾지 못함(없거나 삭제됨). */
	SELF_INTRO_POST_NOT_FOUND("LOUNGE-008", "셀소를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	// 라운지 대화 신청
	/** 본인이 작성한 셀소에 대화를 신청함. */
	LOUNGE_CHAT_REQUEST_SELF("LOUNGE-009", "본인이 작성한 글에는 대화를 신청할 수 없습니다.", HttpStatus.BAD_REQUEST),

	/** 같은 글에 이미 대화를 신청함. */
	LOUNGE_CHAT_REQUEST_DUPLICATED("LOUNGE-010", "이미 대화를 신청한 글입니다.", HttpStatus.CONFLICT),

	/** 본인이 작성한 글이 아님. (신청 목록 조회·수락) */
	LOUNGE_POST_NOT_OWNED("LOUNGE-011", "본인이 작성한 글이 아닙니다.", HttpStatus.FORBIDDEN),

	/** 대화 신청을 id로 찾지 못함(없거나 삭제됨). */
	LOUNGE_CHAT_REQUEST_NOT_FOUND("LOUNGE-012", "대화 신청을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 이미 수락한 대화 신청. */
	LOUNGE_CHAT_REQUEST_ALREADY_ACCEPTED("LOUNGE-013", "이미 수락한 대화 신청입니다.", HttpStatus.CONFLICT),
}
```

- [ ] **Step 9: `LockKeyConstraints`에 락 접두사 2개 추가**

`EXTRA_INTRO` 아래에 추가한다:

```kotlin
	/**
	 * 라운지 셀소 대화 신청 처리 락. (postId, userId)로 잠가 같은 사용자의 같은 글 신청을 직렬화한다.
	 * 경합 대상이 "이 사용자가 이 글에 신청했는가"라는 유니크 조건이므로 글 단위가 아니라 글+사용자로 잠근다.
	 * (글 단위로 잠그면 서로 다른 신청자끼리 불필요하게 직렬화된다)
	 * 동시 요청·더블클릭으로 인한 코인 이중 차감을 막는다. (waitTime=0이면 겹친 요청은 즉시 실패)
	 */
	const val LOUNGE_CHAT_REQUEST: String = "LOUNGE_CHAT_REQUEST"

	/**
	 * 라운지 셀소 대화 신청 수락 처리 락. requestId로 잠가 신청 한 건의 상태 전이를 직렬화한다.
	 * 동시 요청·더블클릭으로 인한 코인 이중 차감과 중복 채팅방 생성을 막는다.
	 */
	const val LOUNGE_CHAT_ACCEPT: String = "LOUNGE_CHAT_ACCEPT"
}
```

- [ ] **Step 10: 전체 컴파일 확인**

Run: `./gradlew compileKotlin compileTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 11: 영향받은 기존 테스트 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.report.*" --tests "com.org.oneulsogae.api.chat.*"`
Expected: BUILD SUCCESSFUL (전부 통과 — `when` 분기 추가는 기존 동작을 바꾸지 않는다)

- [ ] **Step 12: 커밋**

```bash
git add oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core
git commit -m "feat(lounge): 대화 신청용 공통 enum·에러코드·락 상수 추가"
```

---

## Task 2: `LoungeChatRequest` 도메인 모델

신청 생성·수락 규칙을 도메인에 캡슐화한다. 서비스에 `if…throw`를 나열하지 않기 위한 단계다.

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/command/domain/LoungeChatRequest.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/lounge/LoungeChatRequestTest.kt`

**Interfaces:**
- Consumes: `LoungeChatRequestStatus`, `LoungeErrorCode` (Task 1)
- Produces:
  - `LoungeChatRequest(id: Long = 0, postId: Long, requesterUserId: Long, status: LoungeChatRequestStatus = PENDING, createdAt: LocalDateTime? = null)`
  - `LoungeChatRequest.create(postId: Long, requesterUserId: Long, postAuthorUserId: Long): LoungeChatRequest`
  - `LoungeChatRequest.acceptBy(postAuthorUserId: Long, actorUserId: Long): LoungeChatRequest`

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/lounge/LoungeChatRequestTest.kt`:

```kotlin
package com.org.oneulsogae.domain.lounge

import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.lounge.LoungeErrorCode
import com.org.oneulsogae.core.lounge.command.domain.LoungeChatRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [LoungeChatRequest] 도메인 유닛 테스트.
 * 생성(본인 글 차단)과 수락(소유권·중복 수락 차단, 상태 전이) 규칙이 도메인에 캡슐화됐는지 검증한다.
 */
class LoungeChatRequestTest : DescribeSpec({

	val postId = 10L
	val authorUserId = 1L
	val requesterUserId = 2L

	describe("create") {

		context("다른 사람의 글에 신청하면") {
			it("PENDING 상태의 신청이 만들어진다") {
				val request: LoungeChatRequest = LoungeChatRequest.create(
					postId = postId,
					requesterUserId = requesterUserId,
					postAuthorUserId = authorUserId,
				)

				request.postId shouldBe postId
				request.requesterUserId shouldBe requesterUserId
				request.status shouldBe LoungeChatRequestStatus.PENDING
			}
		}

		context("본인이 작성한 글에 신청하면") {
			it("LOUNGE_CHAT_REQUEST_SELF 예외를 던진다") {
				val exception: BusinessException = shouldThrow<BusinessException> {
					LoungeChatRequest.create(
						postId = postId,
						requesterUserId = authorUserId,
						postAuthorUserId = authorUserId,
					)
				}

				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_CHAT_REQUEST_SELF
			}
		}
	}

	describe("acceptBy") {

		context("글 작성자가 PENDING 신청을 수락하면") {
			it("상태가 ACCEPTED로 전이된 새 모델을 반환한다") {
				val request = LoungeChatRequest(id = 100L, postId = postId, requesterUserId = requesterUserId)

				val accepted: LoungeChatRequest = request.acceptBy(
					postAuthorUserId = authorUserId,
					actorUserId = authorUserId,
				)

				accepted.status shouldBe LoungeChatRequestStatus.ACCEPTED
				accepted.id shouldBe 100L
				// 원본은 불변이다.
				request.status shouldBe LoungeChatRequestStatus.PENDING
			}
		}

		context("글 작성자가 아닌 사람이 수락하면") {
			it("LOUNGE_POST_NOT_OWNED 예외를 던진다") {
				val request = LoungeChatRequest(id = 100L, postId = postId, requesterUserId = requesterUserId)

				val exception: BusinessException = shouldThrow<BusinessException> {
					request.acceptBy(postAuthorUserId = authorUserId, actorUserId = requesterUserId)
				}

				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_POST_NOT_OWNED
			}
		}

		context("이미 수락한 신청을 다시 수락하면") {
			it("LOUNGE_CHAT_REQUEST_ALREADY_ACCEPTED 예외를 던진다") {
				val request = LoungeChatRequest(
					id = 100L,
					postId = postId,
					requesterUserId = requesterUserId,
					status = LoungeChatRequestStatus.ACCEPTED,
				)

				val exception: BusinessException = shouldThrow<BusinessException> {
					request.acceptBy(postAuthorUserId = authorUserId, actorUserId = authorUserId)
				}

				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_CHAT_REQUEST_ALREADY_ACCEPTED
			}
		}
	}
})
```

> `BusinessException`의 에러 코드 프로퍼티 이름이 `errorCode`가 아니면(예: `code`),
> `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/common/error/BusinessException.kt`를 읽어
> 실제 프로퍼티 이름에 맞춰 세 군데를 고친다.

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.lounge.LoungeChatRequestTest"`
Expected: 컴파일 실패 — `Unresolved reference: LoungeChatRequest`

- [ ] **Step 3: 도메인 모델을 구현한다**

`oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/command/domain/LoungeChatRequest.kt`:

```kotlin
package com.org.oneulsogae.core.lounge.command.domain

import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.lounge.LoungeErrorCode
import java.time.LocalDateTime

/**
 * 라운지 셀소 대화 신청 도메인 모델.
 * 신청자([requesterUserId])가 셀소 글([postId])의 작성자에게 대화를 신청한 한 건을 나타낸다.
 * 글 작성자는 `lounge_posts.user_id`가 단일 진실원천이라 여기에 복사해 두지 않고, 규칙 판정 시 파라미터로 받는다.
 * 생성된 채팅방도 여기에 두지 않는다(`chat_rooms(match_type=LOUNGE, match_id=이 신청 id)`로 역참조한다).
 * [createdAt]은 영속성(BaseEntity)이 채우므로 저장 전(신규)에는 null이다.
 */
data class LoungeChatRequest(
	val id: Long = 0,
	val postId: Long,
	val requesterUserId: Long,
	val status: LoungeChatRequestStatus = LoungeChatRequestStatus.PENDING,
	val createdAt: LocalDateTime? = null,
) {

	/**
	 * 글 작성자([postAuthorUserId])가 이 신청을 수락해 [LoungeChatRequestStatus.ACCEPTED]로 전이한 새 모델을 반환한다.
	 * - 수락자([actorUserId])가 글 작성자가 아니면 [LoungeErrorCode.LOUNGE_POST_NOT_OWNED]
	 * - 이미 수락한 신청이면 [LoungeErrorCode.LOUNGE_CHAT_REQUEST_ALREADY_ACCEPTED]
	 */
	fun acceptBy(postAuthorUserId: Long, actorUserId: Long): LoungeChatRequest {
		if (postAuthorUserId != actorUserId) {
			throw BusinessException(LoungeErrorCode.LOUNGE_POST_NOT_OWNED)
		}
		if (status == LoungeChatRequestStatus.ACCEPTED) {
			throw BusinessException(LoungeErrorCode.LOUNGE_CHAT_REQUEST_ALREADY_ACCEPTED)
		}
		return copy(status = LoungeChatRequestStatus.ACCEPTED)
	}

	companion object {

		/**
		 * 신규 대화 신청을 만든다. (PENDING 상태로 시작)
		 * 본인이 작성한 글([postAuthorUserId]와 [requesterUserId]가 같음)에는 신청할 수 없다.
		 * ([LoungeErrorCode.LOUNGE_CHAT_REQUEST_SELF])
		 */
		fun create(postId: Long, requesterUserId: Long, postAuthorUserId: Long): LoungeChatRequest {
			if (requesterUserId == postAuthorUserId) {
				throw BusinessException(LoungeErrorCode.LOUNGE_CHAT_REQUEST_SELF)
			}
			return LoungeChatRequest(postId = postId, requesterUserId = requesterUserId)
		}
	}
}
```

- [ ] **Step 4: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.lounge.LoungeChatRequestTest"`
Expected: BUILD SUCCESSFUL — 5개 테스트 통과

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/command/domain/LoungeChatRequest.kt oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/lounge/LoungeChatRequestTest.kt
git commit -m "feat(lounge): 대화 신청 도메인 모델 추가"
```

---

## Task 3: 대화 신청 API

영속성(엔티티·어댑터)부터 컨트롤러까지 신청 경로를 수직으로 완성하고 E2E로 검증한다.

**Files:**
- Create: `docs/migration/lounge_chat_requests.sql`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/lounge/command/entity/LoungeChatRequestEntity.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/lounge/command/mapper/LoungeChatRequestMapper.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/lounge/command/repository/LoungeChatRequestJpaRepository.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/lounge/command/adapter/LoungeChatRequestAdapter.kt`
- Create: `oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/LoungeChatRequestEntityFixture.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/command/application/port/out/GetLoungePostPort.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/command/application/port/out/GetLoungeChatRequestPort.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/command/application/port/out/SaveLoungeChatRequestPort.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/command/application/port/in/RequestLoungeChatUseCase.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/command/application/port/in/result/RequestLoungeChatResult.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/command/application/RequestLoungeChatService.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/lounge/LoungeChatRequestController.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/lounge/response/LoungeChatRequestResponse.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/lounge/command/adapter/LoungePostAdapter.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/lounge/RequestLoungeChatE2ETest.kt`

**Interfaces:**
- Consumes: `LoungeChatRequest.create` (Task 2), `LoungeChatRequestStatus`, `CoinUsageType.LOUNGE_CHAT_INIT`, `LoungeErrorCode.*`, `LockKeyConstraints.LOUNGE_CHAT_REQUEST` (Task 1)
- Produces:
  - `GetLoungePostPort.findById(postId: Long): LoungePost?`
  - `GetLoungeChatRequestPort.existsByPostIdAndRequesterUserId(postId: Long, requesterUserId: Long): Boolean`
  - `GetLoungeChatRequestPort.findById(requestId: Long): LoungeChatRequest?`
  - `SaveLoungeChatRequestPort.save(request: LoungeChatRequest): LoungeChatRequest`
  - `RequestLoungeChatUseCase.request(userId: Long, postId: Long): RequestLoungeChatResult`
  - `RequestLoungeChatResult(requestId: Long)`
  - `LoungeChatRequestEntity(postId: Long, requesterUserId: Long, status: LoungeChatRequestStatus)`
  - `LoungeChatRequestEntityFixture.create(postId, requesterUserId, status)`
  - Q타입 `QLoungeChatRequestEntity.loungeChatRequestEntity` (kapt 생성)

- [ ] **Step 1: 마이그레이션 SQL을 작성한다**

`docs/migration/lounge_chat_requests.sql`:

```sql
-- 라운지 셀소 대화 신청. 신청자가 코인을 내고 글 작성자에게 대화를 신청한 한 건을 보관한다.
-- 글 작성자(수신자)는 lounge_posts.user_id가 단일 진실원천이라 여기에 복사하지 않는다.
-- 생성된 채팅방도 컬럼으로 두지 않는다 — chat_rooms(match_type='LOUNGE', match_id=이 행의 id)로 역참조한다.
CREATE TABLE lounge_chat_requests
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    post_id           BIGINT      NOT NULL COMMENT '대상 셀소 글(lounge_posts.id)',
    requester_user_id BIGINT      NOT NULL COMMENT '대화를 신청한 사용자',
    status            VARCHAR(20) NOT NULL COMMENT 'PENDING / ACCEPTED',
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    deleted_at        DATETIME(6) NULL,
    PRIMARY KEY (id),
    -- 같은 글에 같은 사용자가 두 번 신청하지 못하게 막는 최종 방어선(분산 락을 뚫고 들어온 동시 요청 대비).
    CONSTRAINT ux_post_requester UNIQUE (post_id, requester_user_id),
    -- 글별 신청 목록을 최신순(id desc)으로 seek. 동등 조건(post_id) → 정렬 컬럼(id) 순서.
    INDEX idx_post_id_id (post_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
```

- [ ] **Step 2: 실패하는 E2E 테스트를 작성한다**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/lounge/RequestLoungeChatE2ETest.kt`:

```kotlin
package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.fixture.CoinBalanceEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.LoungePostEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.lounge.command.entity.LoungeChatRequestEntity
import com.org.oneulsogae.infra.lounge.command.entity.LoungePostEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungeChatRequestEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungePostEntity
import io.kotest.matchers.shouldBe
import io.restassured.RestAssured
import org.hamcrest.Matchers

/**
 * `POST /lounge/v1/self-intro-posts/{postId}/chat-requests` E2E 테스트.
 * 신청 행 생성과 코인 32 차감, 본인 글·중복 신청·잔액 부족·없는 글 차단을 검증한다.
 */
class RequestLoungeChatE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QLoungeChatRequestEntity.loungeChatRequestEntity)
		IntegrationUtil.deleteAll(QLoungePostEntity.loungePostEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
	}

	describe("POST /lounge/v1/self-intro-posts/{postId}/chat-requests") {

		context("다른 사람의 셀소에 코인을 갖고 신청하면") {
			it("PENDING 신청이 생성되고 코인 32가 차감된다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-author-1")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-user-1")).id!!
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = requesterId, balance = 100))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(requesterId)}")
					.post("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(200)
					.body("success", Matchers.equalTo(true))
					.body("data.requestId", Matchers.notNullValue())

				val saved: LoungeChatRequestEntity = IntegrationUtil.getQuery()
					.selectFrom(QLoungeChatRequestEntity.loungeChatRequestEntity)
					.where(QLoungeChatRequestEntity.loungeChatRequestEntity.postId.eq(post.id!!))
					.fetchFirst()!!
				saved.requesterUserId shouldBe requesterId
				saved.status shouldBe LoungeChatRequestStatus.PENDING

				val balance: Int = IntegrationUtil.getQuery()
					.select(QCoinBalanceEntity.coinBalanceEntity.balance)
					.from(QCoinBalanceEntity.coinBalanceEntity)
					.where(QCoinBalanceEntity.coinBalanceEntity.userId.eq(requesterId))
					.fetchFirst()!!
				balance shouldBe 68
			}
		}

		context("본인이 쓴 셀소에 신청하면") {
			it("400(LOUNGE-009)을 반환한다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-author-2")).id!!
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = authorId, balance = 100))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.post("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(400)
					.body("error.code", Matchers.equalTo("LOUNGE-009"))
			}
		}

		context("같은 글에 두 번 신청하면") {
			it("두 번째는 409(LOUNGE-010)이고 코인은 한 번만 차감된다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-author-3")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-user-3")).id!!
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = requesterId, balance = 100))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(requesterId)}")
					.post("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(200)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(requesterId)}")
					.post("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(409)
					.body("error.code", Matchers.equalTo("LOUNGE-010"))

				val balance: Int = IntegrationUtil.getQuery()
					.select(QCoinBalanceEntity.coinBalanceEntity.balance)
					.from(QCoinBalanceEntity.coinBalanceEntity)
					.where(QCoinBalanceEntity.coinBalanceEntity.userId.eq(requesterId))
					.fetchFirst()!!
				balance shouldBe 68
			}
		}

		context("코인이 부족하면") {
			it("신청 행이 남지 않고 실패한다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-author-4")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-user-4")).id!!
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = requesterId, balance = 5))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(requesterId)}")
					.post("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(400)
					.body("error.code", Matchers.equalTo("COIN-001"))

				val count: Long = IntegrationUtil.getQuery()
					.selectFrom(QLoungeChatRequestEntity.loungeChatRequestEntity)
					.where(QLoungeChatRequestEntity.loungeChatRequestEntity.postId.eq(post.id!!))
					.fetch()
					.size
					.toLong()
				count shouldBe 0L
			}
		}

		context("없는 글에 신청하면") {
			it("404(LOUNGE-008)를 반환한다") {
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-user-5")).id!!
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = requesterId, balance = 100))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(requesterId)}")
					.post("/lounge/v1/self-intro-posts/99999999/chat-requests")
					.then()
					.statusCode(404)
					.body("error.code", Matchers.equalTo("LOUNGE-008"))
			}
		}
	}
})
```

- [ ] **Step 3: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.lounge.RequestLoungeChatE2ETest"`
Expected: 컴파일 실패 — `Unresolved reference: LoungeChatRequestEntity`

- [ ] **Step 4: 영속성 엔티티를 만든다**

`oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/lounge/command/entity/LoungeChatRequestEntity.kt`:

```kotlin
package com.org.oneulsogae.infra.lounge.command.entity

import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction

/**
 * 라운지 셀소 대화 신청 영속성 엔티티.
 * 글 작성자(수신자)는 [LoungePostEntity]의 user_id가 단일 진실원천이라 여기에 복사 저장하지 않는다.
 * 수락으로 생성된 채팅방도 컬럼으로 두지 않는다 — chat_rooms(match_type=LOUNGE, match_id=이 행의 id)로 역참조한다.
 * 삭제는 soft delete(deleted_at)로 처리한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "lounge_chat_requests",
	// 같은 글에 같은 사용자가 두 번 신청하지 못하게 막는 최종 방어선. (분산 락을 뚫고 들어온 동시 요청 대비)
	uniqueConstraints = [
		UniqueConstraint(name = "ux_post_requester", columnNames = ["post_id", "requester_user_id"]),
	],
	indexes = [
		// 글별 신청 목록(최신순) 조회용. 동등 조건(post_id)과 정렬 컬럼(id desc)을 한 인덱스로 받친다.
		Index(name = "idx_post_id_id", columnList = "post_id, id"),
	],
)
class LoungeChatRequestEntity(
	/** 대상 셀소 글([LoungePostEntity])의 id. */
	@Column(name = "post_id", nullable = false)
	val postId: Long,

	/** 대화를 신청한 사용자. */
	@Column(name = "requester_user_id", nullable = false)
	val requesterUserId: Long,

	/** 신청 상태. 수락되면 ACCEPTED로 바뀐다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	var status: LoungeChatRequestStatus = LoungeChatRequestStatus.PENDING,
) : BaseEntity()
```

- [ ] **Step 5: 매퍼·리포지토리·어댑터를 만든다**

`oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/lounge/command/mapper/LoungeChatRequestMapper.kt`:

```kotlin
package com.org.oneulsogae.infra.lounge.command.mapper

import com.org.oneulsogae.core.lounge.command.domain.LoungeChatRequest
import com.org.oneulsogae.infra.lounge.command.entity.LoungeChatRequestEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun LoungeChatRequestEntity.toDomain(): LoungeChatRequest =
	LoungeChatRequest(
		id = id ?: 0,
		postId = postId,
		requesterUserId = requesterUserId,
		status = status,
		createdAt = createdAt,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun LoungeChatRequest.toEntity(): LoungeChatRequestEntity =
	LoungeChatRequestEntity(
		postId = postId,
		requesterUserId = requesterUserId,
		status = status,
	).also { if (id != 0L) it.id = id }
```

`oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/lounge/command/repository/LoungeChatRequestJpaRepository.kt`:

```kotlin
package com.org.oneulsogae.infra.lounge.command.repository

import com.org.oneulsogae.infra.lounge.command.entity.LoungeChatRequestEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 라운지 대화 신청 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 [com.org.oneulsogae.infra.lounge.command.adapter.LoungeChatRequestAdapter]가 구현한다.
 */
interface LoungeChatRequestJpaRepository : JpaRepository<LoungeChatRequestEntity, Long> {

	/** 이 사용자가 이 글에 이미 신청했는지 여부. (ux_post_requester 유니크 인덱스로 seek) */
	fun existsByPostIdAndRequesterUserId(postId: Long, requesterUserId: Long): Boolean
}
```

`oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/lounge/command/adapter/LoungeChatRequestAdapter.kt`:

```kotlin
package com.org.oneulsogae.infra.lounge.command.adapter

import com.org.oneulsogae.core.lounge.command.application.port.out.GetLoungeChatRequestPort
import com.org.oneulsogae.core.lounge.command.application.port.out.SaveLoungeChatRequestPort
import com.org.oneulsogae.core.lounge.command.domain.LoungeChatRequest
import com.org.oneulsogae.infra.lounge.command.mapper.toDomain
import com.org.oneulsogae.infra.lounge.command.mapper.toEntity
import com.org.oneulsogae.infra.lounge.command.repository.LoungeChatRequestJpaRepository
import org.springframework.stereotype.Component

/** 라운지 대화 신청 엔티티의 out-port 어댑터. (엔티티당 어댑터 하나 — 조회·저장을 함께 구현) */
@Component
class LoungeChatRequestAdapter(
	private val loungeChatRequestJpaRepository: LoungeChatRequestJpaRepository,
) : GetLoungeChatRequestPort, SaveLoungeChatRequestPort {

	override fun existsByPostIdAndRequesterUserId(postId: Long, requesterUserId: Long): Boolean =
		loungeChatRequestJpaRepository.existsByPostIdAndRequesterUserId(postId, requesterUserId)

	override fun findById(requestId: Long): LoungeChatRequest? =
		loungeChatRequestJpaRepository.findById(requestId).orElse(null)?.toDomain()

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(request: LoungeChatRequest): LoungeChatRequest =
		loungeChatRequestJpaRepository.save(request.toEntity()).toDomain()
}
```

- [ ] **Step 6: `LoungePostAdapter`에 `GetLoungePostPort` 구현을 더한다**

먼저 out-port 3개를 만든다.

`.../core/lounge/command/application/port/out/GetLoungePostPort.kt`:

```kotlin
package com.org.oneulsogae.core.lounge.command.application.port.out

import com.org.oneulsogae.core.lounge.command.domain.LoungePost

/** 라운지 글(공통 골격) 단건 조회 out-port. 대화 신청·수락에서 글 존재와 작성자를 확인하는 데 쓴다. */
interface GetLoungePostPort {

	/** 글 한 건. 없거나 삭제됐으면 null. */
	fun findById(postId: Long): LoungePost?
}
```

`.../core/lounge/command/application/port/out/GetLoungeChatRequestPort.kt`:

```kotlin
package com.org.oneulsogae.core.lounge.command.application.port.out

import com.org.oneulsogae.core.lounge.command.domain.LoungeChatRequest

/** 라운지 대화 신청 조회 out-port. */
interface GetLoungeChatRequestPort {

	/** 이 사용자가 이 글에 이미 신청했는지 여부. (중복 신청 차단용) */
	fun existsByPostIdAndRequesterUserId(postId: Long, requesterUserId: Long): Boolean

	/** 신청 한 건. 없거나 삭제됐으면 null. */
	fun findById(requestId: Long): LoungeChatRequest?
}
```

`.../core/lounge/command/application/port/out/SaveLoungeChatRequestPort.kt`:

```kotlin
package com.org.oneulsogae.core.lounge.command.application.port.out

import com.org.oneulsogae.core.lounge.command.domain.LoungeChatRequest

/** 라운지 대화 신청 저장 out-port. (신규 저장과 상태 전이 저장을 함께 담당) */
interface SaveLoungeChatRequestPort {

	fun save(request: LoungeChatRequest): LoungeChatRequest
}
```

그리고 `LoungePostAdapter`를 다음으로 바꾼다 (기존 구현은 유지하고 `GetLoungePostPort`만 더한다):

```kotlin
package com.org.oneulsogae.infra.lounge.command.adapter

import com.org.oneulsogae.common.lounge.LoungePostType
import com.org.oneulsogae.core.lounge.command.application.port.out.CountRecentSelfIntroPostPort
import com.org.oneulsogae.core.lounge.command.application.port.out.GetLoungePostPort
import com.org.oneulsogae.core.lounge.command.application.port.out.SaveLoungePostPort
import com.org.oneulsogae.core.lounge.command.domain.LoungePost
import com.org.oneulsogae.infra.lounge.command.mapper.toDomain
import com.org.oneulsogae.infra.lounge.command.mapper.toEntity
import com.org.oneulsogae.infra.lounge.command.repository.LoungePostJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 라운지 글(공통 골격) 엔티티의 out-port 어댑터. (엔티티당 어댑터 하나)
 * 저장([SaveLoungePostPort])과 등록 빈도 판단용 카운트([CountRecentSelfIntroPostPort]),
 * 대화 신청에서 쓰는 단건 조회([GetLoungePostPort])를 함께 구현한다.
 */
@Component
class LoungePostAdapter(
	private val loungePostJpaRepository: LoungePostJpaRepository,
) : SaveLoungePostPort, CountRecentSelfIntroPostPort, GetLoungePostPort {

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(post: LoungePost): LoungePost =
		loungePostJpaRepository.save(post.toEntity()).toDomain()

	override fun countSelfIntroPostsCreatedAfter(userId: Long, since: LocalDateTime): Int =
		loungePostJpaRepository.countByUserIdAndTypeAndCreatedAtAfter(userId, LoungePostType.SELF_INTRO, since)

	// @SQLRestriction("deleted_at is null")이 걸려 있어 소프트 삭제된 글은 조회되지 않는다.
	override fun findById(postId: Long): LoungePost? =
		loungePostJpaRepository.findById(postId).orElse(null)?.toDomain()
}
```

- [ ] **Step 7: in-port와 결과 타입을 만든다**

`.../core/lounge/command/application/port/in/result/RequestLoungeChatResult.kt`:

```kotlin
package com.org.oneulsogae.core.lounge.command.application.port.`in`.result

/** 대화 신청 결과. 생성된 신청의 id를 돌려준다. (수락 API의 키) */
data class RequestLoungeChatResult(
	val requestId: Long,
)
```

`.../core/lounge/command/application/port/in/RequestLoungeChatUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.lounge.command.application.port.`in`

import com.org.oneulsogae.core.lounge.command.application.port.`in`.result.RequestLoungeChatResult

/**
 * 라운지 셀소 글 작성자에게 대화를 신청하는 인포트(유스케이스).
 * 신청 비용(코인)은 서버가 산출해 차감한다.
 */
interface RequestLoungeChatUseCase {

	fun request(userId: Long, postId: Long): RequestLoungeChatResult
}
```

- [ ] **Step 8: 신청 서비스를 구현한다**

`.../core/lounge/command/application/RequestLoungeChatService.kt`:

```kotlin
package com.org.oneulsogae.core.lounge.command.application

import com.org.oneulsogae.common.coin.CoinUsageType
import com.org.oneulsogae.core.coin.command.application.port.`in`.SpendCoinUseCase
import com.org.oneulsogae.core.coin.command.application.port.`in`.command.SpendCoinCommand
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.lock.DistributedLock
import com.org.oneulsogae.core.common.lock.LockKeyConstraints
import com.org.oneulsogae.core.lounge.LoungeErrorCode
import com.org.oneulsogae.core.lounge.command.application.port.`in`.RequestLoungeChatUseCase
import com.org.oneulsogae.core.lounge.command.application.port.`in`.result.RequestLoungeChatResult
import com.org.oneulsogae.core.lounge.command.application.port.out.GetLoungeChatRequestPort
import com.org.oneulsogae.core.lounge.command.application.port.out.GetLoungePostPort
import com.org.oneulsogae.core.lounge.command.application.port.out.SaveLoungeChatRequestPort
import com.org.oneulsogae.core.lounge.command.domain.LoungeChatRequest
import com.org.oneulsogae.core.lounge.command.domain.LoungePost
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [RequestLoungeChatUseCase] 구현.
 * 글 존재·중복 신청을 확인한 뒤 신청을 저장하고 신청 비용을 차감한다.
 * 본인 글 신청 차단은 도메인([LoungeChatRequest.create])이 판정한다.
 * 차감액은 [CoinUsageType.LOUNGE_CHAT_INIT]의 정책값이라 클라이언트가 금액을 정하지 않는다.
 * 코인 도메인은 자기 out-port가 아니라 in-port([SpendCoinUseCase])로 참조한다.
 * 신청 저장과 코인 차감은 같은 트랜잭션이라 한 단계라도 실패하면 함께 롤백된다.
 *
 * (postId, userId) 분산 락([DistributedLock])으로 보호한다. 경합 대상이 "이 사용자가 이 글에 신청했는가"라는
 * 유니크 조건이므로 글 단위가 아니라 글+사용자로 잠근다. (글로 잠그면 서로 다른 신청자끼리 불필요하게 직렬화된다)
 * waitTime=0이라 겹친 요청은 즉시 실패(409)한다. (더블클릭 이중 과금 fail-fast)
 */
@Service
class RequestLoungeChatService(
	private val getLoungePostPort: GetLoungePostPort,
	private val getLoungeChatRequestPort: GetLoungeChatRequestPort,
	private val saveLoungeChatRequestPort: SaveLoungeChatRequestPort,
	private val spendCoinUseCase: SpendCoinUseCase,
) : RequestLoungeChatUseCase {

	@DistributedLock(prefix = LockKeyConstraints.LOUNGE_CHAT_REQUEST, keys = ["#postId", "#userId"], waitTime = 0)
	@Transactional
	override fun request(userId: Long, postId: Long): RequestLoungeChatResult {
		val post: LoungePost = getLoungePostPort.findById(postId)
			?: throw BusinessException(LoungeErrorCode.SELF_INTRO_POST_NOT_FOUND, "셀소를 찾을 수 없습니다: $postId")

		if (getLoungeChatRequestPort.existsByPostIdAndRequesterUserId(postId, userId)) {
			throw BusinessException(LoungeErrorCode.LOUNGE_CHAT_REQUEST_DUPLICATED)
		}

		val saved: LoungeChatRequest = saveLoungeChatRequestPort.save(
			LoungeChatRequest.create(postId = postId, requesterUserId = userId, postAuthorUserId = post.userId),
		)
		spendCoinUseCase.spend(userId, SpendCoinCommand(amount = USAGE_TYPE.coinAmount, coinUsageType = USAGE_TYPE))

		return RequestLoungeChatResult(saved.id)
	}

	companion object {
		/** 대화 신청 차감 유형. 금액은 이 유형의 정책값(coinAmount)을 그대로 쓴다. */
		private val USAGE_TYPE: CoinUsageType = CoinUsageType.LOUNGE_CHAT_INIT
	}
}
```

> `BusinessException`이 두 번째 인자(상세 메시지)를 받지 않으면 `BusinessException(LoungeErrorCode.SELF_INTRO_POST_NOT_FOUND)`로 줄인다.
> (`GetSelfIntroPostsService`가 두 인자 형태를 쓰고 있으므로 받는 것이 정상이다)

- [ ] **Step 9: 응답 DTO와 컨트롤러를 만든다**

`oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/lounge/response/LoungeChatRequestResponse.kt`:

```kotlin
package com.org.oneulsogae.api.lounge.response

import com.org.oneulsogae.core.lounge.command.application.port.`in`.result.RequestLoungeChatResult

/** 대화 신청 응답. [requestId]는 작성자가 수락할 때 쓰는 키다. */
data class LoungeChatRequestResponse(
	val requestId: Long,
) {
	companion object {

		fun of(result: RequestLoungeChatResult): LoungeChatRequestResponse =
			LoungeChatRequestResponse(requestId = result.requestId)
	}
}
```

`oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/lounge/LoungeChatRequestController.kt`:

```kotlin
package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.api.lounge.response.LoungeChatRequestResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.lounge.command.application.port.`in`.RequestLoungeChatUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 라운지 셀소 대화 신청 엔드포인트. (인증 필요)
 * - POST /lounge/v1/self-intro-posts/{postId}/chat-requests: 셀소 작성자에게 대화를 신청한다. (코인 차감)
 */
@RestController
@RequestMapping("/lounge/v1")
@Tag(name = "라운지 대화 신청", description = "라운지 셀소 대화 신청·수락 엔드포인트 (인증 필요)")
class LoungeChatRequestController(
	private val requestLoungeChatUseCase: RequestLoungeChatUseCase,
) {

	/** 셀소 작성자에게 대화를 신청한다. 신청 코인(32)이 차감된다. */
	@Operation(
		summary = "대화 신청",
		description = "라운지 셀소 상세에서 작성자에게 대화를 신청한다. 신청 코인 32가 차감되고 신청은 PENDING 상태로 저장된다. 본인 글이면 400(LOUNGE-009), 이미 신청한 글이면 409(LOUNGE-010), 글이 없으면 404(LOUNGE-008)를 반환한다.",
	)
	@PostMapping("/self-intro-posts/{postId}/chat-requests")
	fun requestChat(
		@LoginUser user: AuthUser,
		@PathVariable("postId") postId: Long,
	): ApiResponse<LoungeChatRequestResponse> =
		ApiResponse.success(LoungeChatRequestResponse.of(requestLoungeChatUseCase.request(user.id, postId)))
}
```

- [ ] **Step 10: 테스트 픽스처를 만든다**

`oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/LoungeChatRequestEntityFixture.kt`:

```kotlin
package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.infra.lounge.command.entity.LoungeChatRequestEntity

/** [LoungeChatRequestEntity] 테스트 픽스처. 기본은 아직 수락되지 않은(PENDING) 신청이다. */
object LoungeChatRequestEntityFixture {

	fun create(
		postId: Long = 1L,
		requesterUserId: Long = 1L,
		status: LoungeChatRequestStatus = LoungeChatRequestStatus.PENDING,
	): LoungeChatRequestEntity =
		LoungeChatRequestEntity(
			postId = postId,
			requesterUserId = requesterUserId,
			status = status,
		)
}
```

- [ ] **Step 11: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.lounge.RequestLoungeChatE2ETest"`
Expected: BUILD SUCCESSFUL — 5개 시나리오 통과

- [ ] **Step 12: 커밋**

```bash
git add docs/migration/lounge_chat_requests.sql oneulsogae-core oneulsogae-infra oneulsogae-api
git commit -m "feat(lounge): 셀소 대화 신청 API 추가"
```

---

## Task 4: 받은 대화 신청 목록 조회 API

작성자가 자기 셀소에 온 신청자들을 프로필과 함께 최신순으로 본다. query 패키지는 자기 dao에만 의존한다.

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/query/dto/LoungeChatRequestView.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/query/dto/LoungeChatRequestPage.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/query/dao/GetLoungeChatRequestDao.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/query/service/port/in/GetLoungeChatRequestsUseCase.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/query/service/GetLoungeChatRequestsService.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/lounge/query/GetLoungeChatRequestDaoImpl.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/lounge/response/LoungeChatRequestPageResponse.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/lounge/LoungeChatRequestController.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/lounge/GetLoungeChatRequestsE2ETest.kt`

**Interfaces:**
- Consumes: `LoungeChatRequestEntity` + `QLoungeChatRequestEntity` (Task 3), `ChatRoomMatchType.LOUNGE`, `LoungeChatRequestStatus`, `LoungeErrorCode.LOUNGE_POST_NOT_OWNED` / `SELF_INTRO_POST_NOT_FOUND` (Task 1)
- Produces:
  - `LoungeChatRequestView(requestId, userId, nickname, gender, birthday, status, chatRoomId, requestedAt, age)`
  - `LoungeChatRequestPage.of(rows: List<LoungeChatRequestView>, size: Int)`, `.withAges(today: LocalDate)`, `.values`, `.hasNext`, `.nextCursor`
  - `GetLoungeChatRequestDao.findAuthorUserIdByPostId(postId: Long): Long?`
  - `GetLoungeChatRequestDao.findPageByPostId(postId: Long, beforeId: Long?, limit: Int): List<LoungeChatRequestView>`
  - `GetLoungeChatRequestsUseCase.getRequests(userId: Long, postId: Long, cursor: Long?): LoungeChatRequestPage`

- [ ] **Step 1: 실패하는 E2E 테스트를 작성한다**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/lounge/GetLoungeChatRequestsE2ETest.kt`:

```kotlin
package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.common.chat.ChatRoomMatchType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.chat.command.entity.ChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomEntity
import com.org.oneulsogae.infra.fixture.ChatRoomEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.LoungeChatRequestEntityFixture
import com.org.oneulsogae.infra.fixture.LoungePostEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.lounge.command.entity.LoungeChatRequestEntity
import com.org.oneulsogae.infra.lounge.command.entity.LoungePostEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungeChatRequestEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungePostEntity
import io.restassured.RestAssured
import org.hamcrest.Matchers
import java.time.LocalDate
import java.time.Period

/**
 * `GET /lounge/v1/self-intro-posts/{postId}/chat-requests` E2E 테스트.
 * 작성자가 받은 신청을 신청자 프로필·상태·채팅방 id와 함께 최신순으로 받는지, 남의 글은 막히는지 검증한다.
 */
class GetLoungeChatRequestsE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		IntegrationUtil.deleteAll(QLoungeChatRequestEntity.loungeChatRequestEntity)
		IntegrationUtil.deleteAll(QLoungePostEntity.loungePostEntity)
	}

	describe("GET /lounge/v1/self-intro-posts/{postId}/chat-requests") {

		context("작성자가 자기 글의 신청 목록을 조회하면") {
			it("신청자 프로필·상태를 최신순으로 내려주고 수락된 건에만 chatRoomId가 채워진다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-author-1")).id!!
				val olderRequesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-user-1")).id!!
				val newerRequesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-user-2")).id!!
				val birthday: LocalDate = LocalDate.of(1995, 3, 3)
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(
						userId = olderRequesterId,
						nickname = "먼저신청",
						gender = Gender.MALE,
						birthday = birthday,
					),
				)
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(
						userId = newerRequesterId,
						nickname = "나중신청",
						gender = Gender.MALE,
						birthday = birthday,
					),
				)
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				val olderRequest: LoungeChatRequestEntity = IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(
						postId = post.id!!,
						requesterUserId = olderRequesterId,
						status = LoungeChatRequestStatus.ACCEPTED,
					),
				)
				IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(
						postId = post.id!!,
						requesterUserId = newerRequesterId,
						status = LoungeChatRequestStatus.PENDING,
					),
				)
				val chatRoom: ChatRoomEntity = IntegrationUtil.persist(
					ChatRoomEntityFixture.create(matchType = ChatRoomMatchType.LOUNGE, matchId = olderRequest.id!!),
				)
				val expectedAge: Int = Period.between(birthday, LocalDate.now()).years

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.get("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(200)
					.body("success", Matchers.equalTo(true))
					.body("data.items", Matchers.hasSize<Any>(2))
					// 최신순이라 나중에 신청한 사람이 앞에 온다.
					.body("data.items[0].nickname", Matchers.equalTo("나중신청"))
					.body("data.items[0].status", Matchers.equalTo("PENDING"))
					.body("data.items[0].chatRoomId", Matchers.nullValue())
					.body("data.items[0].age", Matchers.equalTo(expectedAge))
					.body("data.items[0].gender", Matchers.equalTo("MALE"))
					.body("data.items[1].nickname", Matchers.equalTo("먼저신청"))
					.body("data.items[1].status", Matchers.equalTo("ACCEPTED"))
					.body("data.items[1].chatRoomId", Matchers.equalTo(chatRoom.id!!.toInt()))
					.body("data.hasNext", Matchers.equalTo(false))
					.body("data.nextCursor", Matchers.nullValue())
			}
		}

		context("남의 글의 신청 목록을 조회하면") {
			it("403(LOUNGE-011)을 반환한다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-author-2")).id!!
				val otherId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-user-3")).id!!
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(otherId)}")
					.get("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(403)
					.body("error.code", Matchers.equalTo("LOUNGE-011"))
			}
		}

		context("없는 글의 신청 목록을 조회하면") {
			it("404(LOUNGE-008)를 반환한다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-user-4")).id!!

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.get("/lounge/v1/self-intro-posts/99999999/chat-requests")
					.then()
					.statusCode(404)
					.body("error.code", Matchers.equalTo("LOUNGE-008"))
			}
		}
	}
})
```

> `UserDetailEntityFixture.create`의 파라미터 이름·필수 여부는
> `oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/UserDetailEntityFixture.kt`를 읽어 맞춘다.

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.lounge.GetLoungeChatRequestsE2ETest"`
Expected: FAIL — 404 (엔드포인트 미구현)

- [ ] **Step 3: read model과 페이지 일급 컬렉션을 만든다**

`.../core/lounge/query/dto/LoungeChatRequestView.kt`:

```kotlin
package com.org.oneulsogae.core.lounge.query.dto

import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.time.ageAt
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 받은 대화 신청 한 건(read model).
 * 신청자의 닉네임·성별·생년월일은 프로필(user_details)에서 조인해 온 표시용 값이다.
 * [chatRoomId]는 수락으로 생성된 채팅방이며, 아직 수락 전(PENDING)이면 null이다.
 * dao는 [birthday]까지 채우고, 서비스가 [age](만 나이)를 채운다.
 */
data class LoungeChatRequestView(
	val requestId: Long,
	/** 신청자 사용자 id. */
	val userId: Long,
	val nickname: String?,
	val gender: Gender?,
	/** 신청자 생년월일. 응답에는 노출하지 않고 [age] 계산에만 쓴다. */
	val birthday: LocalDate?,
	val status: LoungeChatRequestStatus,
	val chatRoomId: Long?,
	val requestedAt: LocalDateTime,
	/** 신청자 만 나이. 서비스가 [birthday]와 기준일로 채운다. (생년월일이 없으면 null) */
	val age: Int? = null,
) {
	/** dao 투영용 생성자. 나이는 서비스가 채운다. */
	constructor(
		requestId: Long,
		userId: Long,
		nickname: String?,
		gender: Gender?,
		birthday: LocalDate?,
		status: LoungeChatRequestStatus,
		chatRoomId: Long?,
		requestedAt: LocalDateTime,
	) : this(requestId, userId, nickname, gender, birthday, status, chatRoomId, requestedAt, null)

	/** 기준일([today])로 만 나이를 채운 신청을 만든다. */
	fun withAge(today: LocalDate): LoungeChatRequestView =
		copy(age = birthday?.ageAt(today))
}
```

`.../core/lounge/query/dto/LoungeChatRequestPage.kt`:

```kotlin
package com.org.oneulsogae.core.lounge.query.dto

import java.time.LocalDate

/**
 * 받은 대화 신청 목록([LoungeChatRequestView])의 커서 페이지(일급 컬렉션).
 * 최신(requestId 내림차순)순 목록과 다음 페이지 존재 여부·커서를 함께 담아, 커서 산출 규칙을 한곳에 응집시킨다.
 */
class LoungeChatRequestPage private constructor(
	/** 현재 페이지의 신청 목록. 최신(requestId 내림차순)순. */
	val values: List<LoungeChatRequestView>,
	/** 다음(더 과거) 페이지가 있는지 여부. */
	val hasNext: Boolean,
) {

	/** 다음(더 과거) 페이지 조회의 기준 커서. 현재 페이지 마지막(가장 오래된) 신청의 requestId이며, 다음 페이지가 없으면 null. */
	val nextCursor: Long?
		get() = if (hasNext) values.lastOrNull()?.requestId else null

	/** 각 항목의 만 나이를 기준일([today])로 채운 페이지를 만든다. */
	fun withAges(today: LocalDate): LoungeChatRequestPage =
		LoungeChatRequestPage(
			values = values.map { view: LoungeChatRequestView -> view.withAge(today) },
			hasNext = hasNext,
		)

	companion object {

		/**
		 * "한 건 더 읽기(size + 1)"로 조회한 행들로 페이지를 만든다.
		 * [rows]가 [size]보다 많으면 다음 페이지가 있는 것으로 보고, 초과분은 잘라낸다.
		 */
		fun of(rows: List<LoungeChatRequestView>, size: Int): LoungeChatRequestPage =
			LoungeChatRequestPage(values = rows.take(size), hasNext = rows.size > size)
	}
}
```

- [ ] **Step 4: dao 인터페이스와 in-port를 만든다**

`.../core/lounge/query/dao/GetLoungeChatRequestDao.kt`:

```kotlin
package com.org.oneulsogae.core.lounge.query.dao

import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestView

/** 받은 대화 신청 조회 dao. (조회 전용) */
interface GetLoungeChatRequestDao {

	/** 글 작성자의 사용자 id. 글이 없거나 삭제됐으면 null. (소유권 검증용) */
	fun findAuthorUserIdByPostId(postId: Long): Long?

	/**
	 * 글에 온 신청을 최신(requestId 내림차순)순으로 최대 [limit]건 조회한다.
	 * [beforeId]를 주면 그보다 과거(requestId 미만) 구간을 잇는다. (커서 페이징)
	 */
	fun findPageByPostId(postId: Long, beforeId: Long?, limit: Int): List<LoungeChatRequestView>
}
```

`.../core/lounge/query/service/port/in/GetLoungeChatRequestsUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.lounge.query.service.port.`in`

import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestPage

/**
 * 내 셀소에 온 대화 신청 목록 조회 유스케이스.
 * 신청이 많아질 수 있어 커서 기반으로 한 페이지씩 내려준다.
 */
interface GetLoungeChatRequestsUseCase {

	/** [userId]가 작성한 글([postId])에 온 신청 한 페이지. 남의 글이면 거절한다. */
	fun getRequests(userId: Long, postId: Long, cursor: Long?): LoungeChatRequestPage
}
```

- [ ] **Step 5: 조회 서비스를 구현한다**

`.../core/lounge/query/service/GetLoungeChatRequestsService.kt`:

```kotlin
package com.org.oneulsogae.core.lounge.query.service

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.lounge.LoungeErrorCode
import com.org.oneulsogae.core.lounge.query.dao.GetLoungeChatRequestDao
import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestPage
import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestView
import com.org.oneulsogae.core.lounge.query.service.port.`in`.GetLoungeChatRequestsUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetLoungeChatRequestsUseCase] 구현. (조회 전용 - 쓰기 부수효과 없음)
 * 조회 dao([GetLoungeChatRequestDao])에만 의존한다. 소유권 검증에 쓰는 글 작성자도 자기 dao로 읽는다.
 * (command의 GetLoungePostPort를 공유하지 않는다 — query는 command 포트를 참조하지 않는다)
 * 목록은 페이지 크기 + 1건을 읽어 다음 페이지 존재 여부를 판정한다. (COUNT 없이 커서 페이징)
 * 신청자의 만 나이는 [TimeGenerator]의 오늘 날짜로 계산한다.
 */
@Service
@Transactional(readOnly = true)
class GetLoungeChatRequestsService(
	private val getLoungeChatRequestDao: GetLoungeChatRequestDao,
	private val timeGenerator: TimeGenerator,
) : GetLoungeChatRequestsUseCase {

	override fun getRequests(userId: Long, postId: Long, cursor: Long?): LoungeChatRequestPage {
		val authorUserId: Long = getLoungeChatRequestDao.findAuthorUserIdByPostId(postId)
			?: throw BusinessException(LoungeErrorCode.SELF_INTRO_POST_NOT_FOUND, "셀소를 찾을 수 없습니다: $postId")
		if (authorUserId != userId) {
			throw BusinessException(LoungeErrorCode.LOUNGE_POST_NOT_OWNED)
		}

		val rows: List<LoungeChatRequestView> = getLoungeChatRequestDao.findPageByPostId(postId, cursor, PAGE_SIZE + 1)
		return LoungeChatRequestPage.of(rows, PAGE_SIZE).withAges(timeGenerator.today())
	}

	companion object {
		/** 한 페이지에 내려주는 신청 건수. */
		const val PAGE_SIZE: Int = 20
	}
}
```

- [ ] **Step 6: QueryDSL dao 구현을 만든다**

`oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/lounge/query/GetLoungeChatRequestDaoImpl.kt`:

```kotlin
package com.org.oneulsogae.infra.lounge.query

import com.org.oneulsogae.common.chat.ChatRoomMatchType
import com.org.oneulsogae.core.lounge.query.dao.GetLoungeChatRequestDao
import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestView
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungeChatRequestEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungePostEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetLoungeChatRequestDao]의 QueryDSL 구현. (조회 전용)
 * 엔티티를 거치지 않고 [LoungeChatRequestView] read model로 바로 투영한다. 만 나이는 서비스가 birthday로 채운다.
 * 신청자 프로필(user_details)은 프로필이 없어도 신청은 보여야 하므로 left join한다.
 * 채팅방은 수락 전에는 없으므로 left join하며, `(match_type, match_id)` 유니크 인덱스로 seek한다.
 * post_id 동등 + id 내림차순 keyset(`id < :beforeId`)이 `idx_post_id_id`로 받쳐져 뒤 페이지에서도 seek로 끝난다(offset 스캔 없음).
 */
@Component
class GetLoungeChatRequestDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetLoungeChatRequestDao {

	override fun findAuthorUserIdByPostId(postId: Long): Long? {
		val post: QLoungePostEntity = QLoungePostEntity.loungePostEntity
		return queryFactory
			.select(post.userId)
			.from(post)
			.where(post.id.eq(postId))
			.fetchFirst()
	}

	override fun findPageByPostId(postId: Long, beforeId: Long?, limit: Int): List<LoungeChatRequestView> {
		val request: QLoungeChatRequestEntity = QLoungeChatRequestEntity.loungeChatRequestEntity
		val userDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val chatRoom: QChatRoomEntity = QChatRoomEntity.chatRoomEntity
		return queryFactory
			.select(
				Projections.constructor(
					LoungeChatRequestView::class.java,
					request.id,
					request.requesterUserId,
					userDetail.nickname,
					userDetail.gender,
					userDetail.birthday,
					request.status,
					chatRoom.id,
					request.createdAt,
				),
			)
			.from(request)
			.leftJoin(userDetail).on(userDetail.userId.eq(request.requesterUserId))
			.leftJoin(chatRoom).on(
				chatRoom.matchType.eq(ChatRoomMatchType.LOUNGE).and(chatRoom.matchId.eq(request.id)),
			)
			.where(
				request.postId.eq(postId),
				beforeId?.let { cursor: Long -> request.id.lt(cursor) },
			)
			.orderBy(request.id.desc())
			.limit(limit.toLong())
			.fetch()
	}
}
```

- [ ] **Step 7: 목록 응답 DTO를 만든다**

`oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/lounge/response/LoungeChatRequestPageResponse.kt`:

```kotlin
package com.org.oneulsogae.api.lounge.response

import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestPage
import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestView
import java.time.LocalDateTime

/**
 * 받은 대화 신청 목록(커서 페이지) 응답.
 * [nextCursor]를 다음 요청의 `cursor`로 그대로 넘기면 이어지는 페이지를 받는다. (다음 페이지가 없으면 null)
 */
data class LoungeChatRequestPageResponse(
	val items: List<LoungeChatRequestItemResponse>,
	val hasNext: Boolean,
	val nextCursor: Long?,
) {
	companion object {

		fun of(page: LoungeChatRequestPage): LoungeChatRequestPageResponse =
			LoungeChatRequestPageResponse(
				items = page.values.map { view: LoungeChatRequestView -> LoungeChatRequestItemResponse.of(view) },
				hasNext = page.hasNext,
				nextCursor = page.nextCursor,
			)
	}
}

/** 신청자 카드 한 장. [chatRoomId]는 아직 수락 전(PENDING)이면 null이다. */
data class LoungeChatRequestItemResponse(
	val requestId: Long,
	val userId: Long,
	val nickname: String?,
	val gender: Gender?,
	val age: Int?,
	val status: LoungeChatRequestStatus,
	val chatRoomId: Long?,
	val requestedAt: LocalDateTime,
) {
	companion object {

		fun of(view: LoungeChatRequestView): LoungeChatRequestItemResponse =
			LoungeChatRequestItemResponse(
				requestId = view.requestId,
				userId = view.userId,
				nickname = view.nickname,
				gender = view.gender,
				age = view.age,
				status = view.status,
				chatRoomId = view.chatRoomId,
				requestedAt = view.requestedAt,
			)
	}
}
```

- [ ] **Step 8: 컨트롤러에 목록 엔드포인트를 더한다**

`LoungeChatRequestController`에 in-port를 추가 주입하고 메서드를 더한다. 임포트도 함께 추가한다:

```kotlin
import com.org.oneulsogae.api.lounge.response.LoungeChatRequestPageResponse
import com.org.oneulsogae.core.lounge.query.service.port.`in`.GetLoungeChatRequestsUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
```

```kotlin
class LoungeChatRequestController(
	private val requestLoungeChatUseCase: RequestLoungeChatUseCase,
	private val getLoungeChatRequestsUseCase: GetLoungeChatRequestsUseCase,
) {
```

```kotlin
	/** 내 셀소에 온 대화 신청 목록을 최신순 한 페이지 조회한다. */
	@Operation(
		summary = "받은 대화 신청 목록 조회",
		description = "내가 쓴 셀소에 온 대화 신청을 최신순으로 20개씩 내려준다. 각 항목은 신청 식별자(requestId)·신청자(userId·닉네임·성별·만 나이)·상태(PENDING/ACCEPTED)·수락으로 생긴 채팅방 id(수락 전이면 null)·신청 시각을 담는다. 다음 페이지는 응답의 nextCursor를 cursor 파라미터로 그대로 넘겨 조회한다(hasNext=false면 마지막 페이지). 내 글이 아니면 403(LOUNGE-011), 글이 없으면 404(LOUNGE-008)를 반환한다.",
	)
	@GetMapping("/self-intro-posts/{postId}/chat-requests")
	fun getChatRequests(
		@LoginUser user: AuthUser,
		@PathVariable("postId") postId: Long,
		@RequestParam("cursor", required = false) cursor: Long?,
	): ApiResponse<LoungeChatRequestPageResponse> =
		ApiResponse.success(
			LoungeChatRequestPageResponse.of(getLoungeChatRequestsUseCase.getRequests(user.id, postId, cursor)),
		)
```

또한 클래스 KDoc에 엔드포인트 한 줄을 더한다:

```
 * - GET /lounge/v1/self-intro-posts/{postId}/chat-requests: 내 셀소에 온 대화 신청 목록을 최신순으로 조회한다.
```

- [ ] **Step 9: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.lounge.GetLoungeChatRequestsE2ETest"`
Expected: BUILD SUCCESSFUL — 3개 시나리오 통과

- [ ] **Step 10: 커밋**

```bash
git add oneulsogae-core oneulsogae-infra oneulsogae-api
git commit -m "feat(lounge): 받은 대화 신청 목록 조회 API 추가"
```

---

## Task 5: 대화 신청 수락 API

작성자가 신청을 수락해 코인을 내고 채팅방을 연다.

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/command/application/port/in/AcceptLoungeChatUseCase.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/command/application/port/in/result/AcceptLoungeChatResult.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/command/application/AcceptLoungeChatService.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/lounge/response/AcceptLoungeChatResponse.kt`
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/lounge/LoungeChatRequestController.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/lounge/AcceptLoungeChatE2ETest.kt`

**Interfaces:**
- Consumes: `LoungeChatRequest.acceptBy` (Task 2), `GetLoungeChatRequestPort`·`SaveLoungeChatRequestPort`·`GetLoungePostPort` (Task 3), `ChatRoomMatchType.LOUNGE`·`CoinUsageType.LOUNGE_CHAT_ACCEPT`·`LockKeyConstraints.LOUNGE_CHAT_ACCEPT` (Task 1), 기존 `SaveChatRoomUseCase.save(SaveChatRoomCommand): ChatRoom`
- Produces:
  - `AcceptLoungeChatUseCase.accept(userId: Long, requestId: Long): AcceptLoungeChatResult`
  - `AcceptLoungeChatResult(chatRoomId: Long)`

- [ ] **Step 1: 실패하는 E2E 테스트를 작성한다**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/lounge/AcceptLoungeChatE2ETest.kt`:

```kotlin
package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.common.chat.ChatRoomMatchType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.infra.chat.command.entity.ChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.fixture.CoinBalanceEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.LoungeChatRequestEntityFixture
import com.org.oneulsogae.infra.fixture.LoungePostEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.lounge.command.entity.LoungeChatRequestEntity
import com.org.oneulsogae.infra.lounge.command.entity.LoungePostEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungeChatRequestEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungePostEntity
import io.kotest.matchers.shouldBe
import io.restassured.RestAssured
import org.hamcrest.Matchers

/**
 * `POST /lounge/v1/chat-requests/{requestId}/accept` E2E 테스트.
 * 수락으로 LOUNGE 채팅방과 참가자 2명이 생기고 코인 32가 차감되는지, 여러 명 수락·중복 수락·권한·잔액 부족을 검증한다.
 */
class AcceptLoungeChatE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		IntegrationUtil.deleteAll(QLoungeChatRequestEntity.loungeChatRequestEntity)
		IntegrationUtil.deleteAll(QLoungePostEntity.loungePostEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
	}

	describe("POST /lounge/v1/chat-requests/{requestId}/accept") {

		context("작성자가 받은 신청을 수락하면") {
			it("LOUNGE 채팅방과 참가자 2명이 생기고 코인 32가 차감된다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-author-1")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-user-1")).id!!
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = authorId, balance = 100))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				val request: LoungeChatRequestEntity = IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(postId = post.id!!, requesterUserId = requesterId),
				)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.post("/lounge/v1/chat-requests/${request.id}/accept")
					.then()
					.statusCode(200)
					.body("success", Matchers.equalTo(true))
					.body("data.chatRoomId", Matchers.notNullValue())

				val chatRoom: ChatRoomEntity = IntegrationUtil.getQuery()
					.selectFrom(QChatRoomEntity.chatRoomEntity)
					.where(
						QChatRoomEntity.chatRoomEntity.matchType.eq(ChatRoomMatchType.LOUNGE),
						QChatRoomEntity.chatRoomEntity.matchId.eq(request.id!!),
					)
					.fetchFirst()!!

				val memberUserIds: List<Long> = IntegrationUtil.getQuery()
					.select(QChatRoomMemberEntity.chatRoomMemberEntity.userId)
					.from(QChatRoomMemberEntity.chatRoomMemberEntity)
					.where(QChatRoomMemberEntity.chatRoomMemberEntity.chatRoomId.eq(chatRoom.id!!))
					.fetch()
				memberUserIds.toSet() shouldBe setOf(authorId, requesterId)

				val status: LoungeChatRequestStatus = IntegrationUtil.getQuery()
					.select(QLoungeChatRequestEntity.loungeChatRequestEntity.status)
					.from(QLoungeChatRequestEntity.loungeChatRequestEntity)
					.where(QLoungeChatRequestEntity.loungeChatRequestEntity.id.eq(request.id!!))
					.fetchFirst()!!
				status shouldBe LoungeChatRequestStatus.ACCEPTED

				val balance: Int = IntegrationUtil.getQuery()
					.select(QCoinBalanceEntity.coinBalanceEntity.balance)
					.from(QCoinBalanceEntity.coinBalanceEntity)
					.where(QCoinBalanceEntity.coinBalanceEntity.userId.eq(authorId))
					.fetchFirst()!!
				balance shouldBe 68
			}
		}

		context("같은 글에 온 신청 두 건을 모두 수락하면") {
			it("채팅방이 두 개 생긴다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-author-2")).id!!
				val firstRequesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-user-2")).id!!
				val secondRequesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-user-3")).id!!
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = authorId, balance = 100))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				val firstRequest: LoungeChatRequestEntity = IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(postId = post.id!!, requesterUserId = firstRequesterId),
				)
				val secondRequest: LoungeChatRequestEntity = IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(postId = post.id!!, requesterUserId = secondRequesterId),
				)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.post("/lounge/v1/chat-requests/${firstRequest.id}/accept")
					.then()
					.statusCode(200)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.post("/lounge/v1/chat-requests/${secondRequest.id}/accept")
					.then()
					.statusCode(200)

				val roomCount: Int = IntegrationUtil.getQuery()
					.selectFrom(QChatRoomEntity.chatRoomEntity)
					.where(QChatRoomEntity.chatRoomEntity.matchType.eq(ChatRoomMatchType.LOUNGE))
					.fetch()
					.size
				roomCount shouldBe 2

				val balance: Int = IntegrationUtil.getQuery()
					.select(QCoinBalanceEntity.coinBalanceEntity.balance)
					.from(QCoinBalanceEntity.coinBalanceEntity)
					.where(QCoinBalanceEntity.coinBalanceEntity.userId.eq(authorId))
					.fetchFirst()!!
				balance shouldBe 36
			}
		}

		context("이미 수락한 신청을 다시 수락하면") {
			it("409(LOUNGE-013)이고 코인은 한 번만 차감된다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-author-3")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-user-4")).id!!
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = authorId, balance = 100))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				val request: LoungeChatRequestEntity = IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(postId = post.id!!, requesterUserId = requesterId),
				)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.post("/lounge/v1/chat-requests/${request.id}/accept")
					.then()
					.statusCode(200)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.post("/lounge/v1/chat-requests/${request.id}/accept")
					.then()
					.statusCode(409)
					.body("error.code", Matchers.equalTo("LOUNGE-013"))

				val balance: Int = IntegrationUtil.getQuery()
					.select(QCoinBalanceEntity.coinBalanceEntity.balance)
					.from(QCoinBalanceEntity.coinBalanceEntity)
					.where(QCoinBalanceEntity.coinBalanceEntity.userId.eq(authorId))
					.fetchFirst()!!
				balance shouldBe 68
			}
		}

		context("작성자가 아닌 사람이 수락하면") {
			it("403(LOUNGE-011)을 반환한다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-author-4")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-user-5")).id!!
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = requesterId, balance = 100))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				val request: LoungeChatRequestEntity = IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(postId = post.id!!, requesterUserId = requesterId),
				)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(requesterId)}")
					.post("/lounge/v1/chat-requests/${request.id}/accept")
					.then()
					.statusCode(403)
					.body("error.code", Matchers.equalTo("LOUNGE-011"))
			}
		}

		context("코인이 부족하면") {
			it("채팅방이 생기지 않고 신청 상태도 그대로다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-author-5")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-user-6")).id!!
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = authorId, balance = 5))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				val request: LoungeChatRequestEntity = IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(postId = post.id!!, requesterUserId = requesterId),
				)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.post("/lounge/v1/chat-requests/${request.id}/accept")
					.then()
					.statusCode(400)
					.body("error.code", Matchers.equalTo("COIN-001"))

				val roomCount: Int = IntegrationUtil.getQuery()
					.selectFrom(QChatRoomEntity.chatRoomEntity)
					.where(QChatRoomEntity.chatRoomEntity.matchId.eq(request.id!!))
					.fetch()
					.size
				roomCount shouldBe 0

				val status: LoungeChatRequestStatus = IntegrationUtil.getQuery()
					.select(QLoungeChatRequestEntity.loungeChatRequestEntity.status)
					.from(QLoungeChatRequestEntity.loungeChatRequestEntity)
					.where(QLoungeChatRequestEntity.loungeChatRequestEntity.id.eq(request.id!!))
					.fetchFirst()!!
				status shouldBe LoungeChatRequestStatus.PENDING
			}
		}

		context("없는 신청을 수락하면") {
			it("404(LOUNGE-012)를 반환한다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-user-7")).id!!
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = userId, balance = 100))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.post("/lounge/v1/chat-requests/99999999/accept")
					.then()
					.statusCode(404)
					.body("error.code", Matchers.equalTo("LOUNGE-012"))
			}
		}
	}
})
```

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.lounge.AcceptLoungeChatE2ETest"`
Expected: FAIL — 404 (엔드포인트 미구현)

- [ ] **Step 3: in-port와 결과 타입을 만든다**

`.../core/lounge/command/application/port/in/result/AcceptLoungeChatResult.kt`:

```kotlin
package com.org.oneulsogae.core.lounge.command.application.port.`in`.result

/** 대화 신청 수락 결과. 생성된 채팅방의 id를 돌려준다. (클라이언트가 바로 채팅방으로 이동하는 키) */
data class AcceptLoungeChatResult(
	val chatRoomId: Long,
)
```

`.../core/lounge/command/application/port/in/AcceptLoungeChatUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.lounge.command.application.port.`in`

import com.org.oneulsogae.core.lounge.command.application.port.`in`.result.AcceptLoungeChatResult

/**
 * 내 셀소에 온 대화 신청을 수락하는 인포트(유스케이스).
 * 수락 비용(코인)이 차감되고 신청자와의 채팅방이 열린다.
 */
interface AcceptLoungeChatUseCase {

	fun accept(userId: Long, requestId: Long): AcceptLoungeChatResult
}
```

- [ ] **Step 4: 수락 서비스를 구현한다**

`.../core/lounge/command/application/AcceptLoungeChatService.kt`:

```kotlin
package com.org.oneulsogae.core.lounge.command.application

import com.org.oneulsogae.common.chat.ChatRoomMatchType
import com.org.oneulsogae.common.coin.CoinUsageType
import com.org.oneulsogae.core.chat.command.application.port.`in`.SaveChatRoomUseCase
import com.org.oneulsogae.core.chat.command.application.port.`in`.command.SaveChatRoomCommand
import com.org.oneulsogae.core.chat.command.application.port.`in`.command.SaveChatRoomParticipant
import com.org.oneulsogae.core.chat.command.domain.ChatRoom
import com.org.oneulsogae.core.coin.command.application.port.`in`.SpendCoinUseCase
import com.org.oneulsogae.core.coin.command.application.port.`in`.command.SpendCoinCommand
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.lock.DistributedLock
import com.org.oneulsogae.core.common.lock.LockKeyConstraints
import com.org.oneulsogae.core.lounge.LoungeErrorCode
import com.org.oneulsogae.core.lounge.command.application.port.`in`.AcceptLoungeChatUseCase
import com.org.oneulsogae.core.lounge.command.application.port.`in`.result.AcceptLoungeChatResult
import com.org.oneulsogae.core.lounge.command.application.port.out.GetLoungeChatRequestPort
import com.org.oneulsogae.core.lounge.command.application.port.out.GetLoungePostPort
import com.org.oneulsogae.core.lounge.command.application.port.out.SaveLoungeChatRequestPort
import com.org.oneulsogae.core.lounge.command.domain.LoungeChatRequest
import com.org.oneulsogae.core.lounge.command.domain.LoungePost
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [AcceptLoungeChatUseCase] 구현.
 * 신청을 로드해 소유권·중복 수락을 도메인([LoungeChatRequest.acceptBy])이 판정하게 하고,
 * 상태 전이 저장 → 수락 비용 차감 → 채팅방 생성을 같은 트랜잭션에서 처리한다. (한 단계라도 실패하면 함께 롤백)
 * 채팅방은 chat 도메인 in-port([SaveChatRoomUseCase])에 위임하며, 신청 한 건당 한 방이 되도록
 * `(match_type=LOUNGE, match_id=신청 id)`로 만든다. (chat 쪽이 이 조합으로 멱등 생성한다)
 * 작성자는 같은 글의 신청을 여러 건 수락할 수 있고, 수락할 때마다 코인을 내고 방이 하나씩 생긴다.
 *
 * 신청별 분산 락([DistributedLock])으로 보호한다. 경합 대상이 신청 한 건의 상태 전이이므로 requestId로 잠근다.
 * waitTime=0이라 겹친 요청은 즉시 실패(409)한다. (더블클릭 이중 과금 fail-fast)
 */
@Service
class AcceptLoungeChatService(
	private val getLoungeChatRequestPort: GetLoungeChatRequestPort,
	private val saveLoungeChatRequestPort: SaveLoungeChatRequestPort,
	private val getLoungePostPort: GetLoungePostPort,
	private val spendCoinUseCase: SpendCoinUseCase,
	private val saveChatRoomUseCase: SaveChatRoomUseCase,
) : AcceptLoungeChatUseCase {

	@DistributedLock(prefix = LockKeyConstraints.LOUNGE_CHAT_ACCEPT, keys = ["#requestId"], waitTime = 0)
	@Transactional
	override fun accept(userId: Long, requestId: Long): AcceptLoungeChatResult {
		val request: LoungeChatRequest = getLoungeChatRequestPort.findById(requestId)
			?: throw BusinessException(LoungeErrorCode.LOUNGE_CHAT_REQUEST_NOT_FOUND)
		val post: LoungePost = getLoungePostPort.findById(request.postId)
			?: throw BusinessException(LoungeErrorCode.SELF_INTRO_POST_NOT_FOUND, "셀소를 찾을 수 없습니다: ${request.postId}")

		// 소유권(내 글인가)·중복 수락 판정은 도메인이 한다.
		saveLoungeChatRequestPort.save(request.acceptBy(postAuthorUserId = post.userId, actorUserId = userId))
		spendCoinUseCase.spend(userId, SpendCoinCommand(amount = USAGE_TYPE.coinAmount, coinUsageType = USAGE_TYPE))

		// 채팅방 생성은 수락의 필수 산출물이라 같은 트랜잭션에서 동기로 처리한다. (실패 시 함께 롤백)
		val chatRoom: ChatRoom = saveChatRoomUseCase.save(
			SaveChatRoomCommand(
				matchType = ChatRoomMatchType.LOUNGE,
				matchId = request.id,
				participants = listOf(
					SaveChatRoomParticipant(userId = post.userId, teamId = null),
					SaveChatRoomParticipant(userId = request.requesterUserId, teamId = null),
				),
			),
		)
		return AcceptLoungeChatResult(chatRoom.id)
	}

	companion object {
		/** 대화 수락 차감 유형. 금액은 이 유형의 정책값(coinAmount)을 그대로 쓴다. */
		private val USAGE_TYPE: CoinUsageType = CoinUsageType.LOUNGE_CHAT_ACCEPT
	}
}
```

- [ ] **Step 5: 응답 DTO를 만든다**

`oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/lounge/response/AcceptLoungeChatResponse.kt`:

```kotlin
package com.org.oneulsogae.api.lounge.response

import com.org.oneulsogae.core.lounge.command.application.port.`in`.result.AcceptLoungeChatResult

/** 대화 신청 수락 응답. [chatRoomId]로 바로 채팅방에 진입한다. */
data class AcceptLoungeChatResponse(
	val chatRoomId: Long,
) {
	companion object {

		fun of(result: AcceptLoungeChatResult): AcceptLoungeChatResponse =
			AcceptLoungeChatResponse(chatRoomId = result.chatRoomId)
	}
}
```

- [ ] **Step 6: 컨트롤러에 수락 엔드포인트를 더한다**

임포트 추가:

```kotlin
import com.org.oneulsogae.api.lounge.response.AcceptLoungeChatResponse
import com.org.oneulsogae.core.lounge.command.application.port.`in`.AcceptLoungeChatUseCase
```

생성자에 주입 추가:

```kotlin
class LoungeChatRequestController(
	private val requestLoungeChatUseCase: RequestLoungeChatUseCase,
	private val getLoungeChatRequestsUseCase: GetLoungeChatRequestsUseCase,
	private val acceptLoungeChatUseCase: AcceptLoungeChatUseCase,
) {
```

메서드 추가:

```kotlin
	/** 내 셀소에 온 대화 신청을 수락해 채팅방을 연다. 수락 코인(32)이 차감된다. */
	@Operation(
		summary = "대화 신청 수락",
		description = "내가 쓴 셀소에 온 대화 신청을 수락한다. 수락 코인 32가 차감되고 신청자와의 채팅방이 생성되며 응답의 chatRoomId로 바로 진입할 수 있다. 같은 글의 신청을 여러 건 수락할 수 있다. 내 글에 온 신청이 아니면 403(LOUNGE-011), 이미 수락했으면 409(LOUNGE-013), 신청이 없으면 404(LOUNGE-012)를 반환한다.",
	)
	@PostMapping("/chat-requests/{requestId}/accept")
	fun acceptChatRequest(
		@LoginUser user: AuthUser,
		@PathVariable("requestId") requestId: Long,
	): ApiResponse<AcceptLoungeChatResponse> =
		ApiResponse.success(AcceptLoungeChatResponse.of(acceptLoungeChatUseCase.accept(user.id, requestId)))
```

클래스 KDoc에도 한 줄 더한다:

```
 * - POST /lounge/v1/chat-requests/{requestId}/accept: 받은 대화 신청을 수락해 채팅방을 연다. (코인 차감)
```

- [ ] **Step 7: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.lounge.AcceptLoungeChatE2ETest"`
Expected: BUILD SUCCESSFUL — 6개 시나리오 통과

- [ ] **Step 8: 커밋**

```bash
git add oneulsogae-core oneulsogae-api
git commit -m "feat(lounge): 대화 신청 수락·채팅방 생성 API 추가"
```

---

## Task 6: 신청·수락 알람

신청과 수락을 커밋 후 알람으로 알린다. 알람 저장이 실패해도 신청·과금·채팅방은 롤백되지 않는다.

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/command/domain/event/LoungeChatRequested.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/command/domain/event/LoungeChatRequestAccepted.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/command/application/LoungeEventHandler.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/command/application/RequestLoungeChatService.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/lounge/command/application/AcceptLoungeChatService.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/lounge/LoungeChatRequestAlarmE2ETest.kt`

**Interfaces:**
- Consumes: `AlarmType.LOUNGE_CHAT_REQUEST_RECEIVED`·`LOUNGE_CHAT_ACCEPTED` (Task 1), `RequestLoungeChatService`·`AcceptLoungeChatService` (Task 3·5), 기존 `DomainEventPublisher.publish(event: Any)`, `SaveAlarmUseCase.save(SaveAlarmCommand)`, `GetUserDetailUseCase.findByUserId(userId: Long)`
- Produces:
  - `LoungeChatRequested(requestId: Long, requesterUserId: Long, postAuthorUserId: Long)`
  - `LoungeChatRequestAccepted(requestId: Long, requesterUserId: Long, postAuthorUserId: Long, chatRoomId: Long)`

- [ ] **Step 1: 실패하는 E2E 테스트를 작성한다**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/lounge/LoungeChatRequestAlarmE2ETest.kt`:

```kotlin
package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.infra.alarm.command.entity.AlarmEntity
import com.org.oneulsogae.infra.alarm.command.entity.QAlarmEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.fixture.CoinBalanceEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.LoungePostEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.lounge.command.entity.LoungePostEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungeChatRequestEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungePostEntity
import io.kotest.matchers.shouldBe
import io.restassured.RestAssured
import io.restassured.path.json.JsonPath

/**
 * 라운지 대화 신청·수락 알람 E2E 테스트.
 * 신청하면 작성자에게 "대화 신청 받음", 수락하면 신청자에게 "대화 신청 수락됨" 알람이 쌓이는지 검증한다.
 * 알람은 AFTER_COMMIT 리스너가 같은 요청 스레드에서 동기로 저장하므로(응답 전 저장 완료),
 * 기존 매칭 알람 E2E([com.org.oneulsogae.api.match.SendInterestE2ETest])와 같이 대기 없이 바로 단언한다.
 */
class LoungeChatRequestAlarmE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
		IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		IntegrationUtil.deleteAll(QLoungeChatRequestEntity.loungeChatRequestEntity)
		IntegrationUtil.deleteAll(QLoungePostEntity.loungePostEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
	}

	describe("라운지 대화 신청·수락 알람") {

		context("신청하고 작성자가 수락하면") {
			it("작성자에게 신청 알람이, 신청자에게 수락 알람이 쌓인다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-alarm-author")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-alarm-user")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = authorId, nickname = "글쓴이"))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = requesterId, nickname = "신청자"))
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = authorId, balance = 100))
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = requesterId, balance = 100))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				val requestBody: String = RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(requesterId)}")
					.post("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(200)
					.extract()
					.asString()
				val requestId: Int = JsonPath(requestBody).getInt("data.requestId")

				// 글 작성자에게 "대화 신청 받음" 알람.
				val requestAlarms: List<AlarmEntity> = alarmsOf(authorId)
				requestAlarms.size shouldBe 1
				val requestAlarm: AlarmEntity = requestAlarms[0]
				requestAlarm.type shouldBe AlarmType.LOUNGE_CHAT_REQUEST_RECEIVED
				requestAlarm.fromUserId shouldBe requesterId
				requestAlarm.description shouldBe "신청자님이 회원님에게 대화를 신청했어요."

				val acceptBody: String = RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.post("/lounge/v1/chat-requests/$requestId/accept")
					.then()
					.statusCode(200)
					.extract()
					.asString()
				val chatRoomId: Int = JsonPath(acceptBody).getInt("data.chatRoomId")

				// 신청자에게 "대화 신청 수락됨" 알람. 누르면 생성된 채팅방으로 이동한다.
				val acceptAlarms: List<AlarmEntity> = alarmsOf(requesterId)
				acceptAlarms.size shouldBe 1
				val acceptAlarm: AlarmEntity = acceptAlarms[0]
				acceptAlarm.type shouldBe AlarmType.LOUNGE_CHAT_ACCEPTED
				acceptAlarm.fromUserId shouldBe authorId
				acceptAlarm.description shouldBe "글쓴이님이 대화 신청을 수락했어요."
				acceptAlarm.link shouldBe "/chat/$chatRoomId"
			}
		}
	}
})

// 해당 사용자의 알람 목록. (알람 저장 확인용 — SendInterestE2ETest와 같은 형태)
private fun alarmsOf(userId: Long): List<AlarmEntity> {
	val alarm: QAlarmEntity = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery()
		.selectFrom(alarm)
		.where(alarm.userId.eq(userId))
		.fetch()
}
```

> `AlarmEntity`의 프로퍼티 이름(`type`·`fromUserId`·`description`·`link`)은
> `oneulsogae-infra/.../alarm/command/entity/AlarmEntity.kt`를 읽어 확인한다.

- [ ] **Step 2: 테스트를 실행해 실패를 확인한다**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.lounge.LoungeChatRequestAlarmE2ETest"`
Expected: FAIL — `requestAlarms.size shouldBe 1`에서 실패 (알람이 저장되지 않아 0건)

- [ ] **Step 3: 도메인 이벤트 2종을 만든다**

`.../core/lounge/command/domain/event/LoungeChatRequested.kt`:

```kotlin
package com.org.oneulsogae.core.lounge.command.domain.event

/**
 * 라운지 셀소에 대화 신청이 생성됐음을 알리는 도메인 이벤트.
 * 수신측([com.org.oneulsogae.core.lounge.command.application.LoungeEventHandler])이 커밋 후 작성자에게 알람을 저장한다.
 */
data class LoungeChatRequested(
	val requestId: Long,
	/** 대화를 신청한 사용자. (알람 문구·fromUserId) */
	val requesterUserId: Long,
	/** 신청을 받은 글 작성자. (알람 수신자) */
	val postAuthorUserId: Long,
)
```

`.../core/lounge/command/domain/event/LoungeChatRequestAccepted.kt`:

```kotlin
package com.org.oneulsogae.core.lounge.command.domain.event

/**
 * 라운지 대화 신청이 수락됐음을 알리는 도메인 이벤트.
 * 수신측([com.org.oneulsogae.core.lounge.command.application.LoungeEventHandler])이 커밋 후 신청자에게 알람을 저장한다.
 */
data class LoungeChatRequestAccepted(
	val requestId: Long,
	/** 신청을 보낸 사용자. (알람 수신자) */
	val requesterUserId: Long,
	/** 신청을 수락한 글 작성자. (알람 문구·fromUserId) */
	val postAuthorUserId: Long,
	/** 수락으로 생성된 채팅방. (알람을 눌러 이동할 대상) */
	val chatRoomId: Long,
)
```

- [ ] **Step 4: 이벤트 핸들러를 만든다**

`.../core/lounge/command/application/LoungeEventHandler.kt`:

```kotlin
package com.org.oneulsogae.core.lounge.command.application

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.core.alarm.command.application.port.`in`.SaveAlarmUseCase
import com.org.oneulsogae.core.alarm.command.application.port.`in`.command.SaveAlarmCommand
import com.org.oneulsogae.core.lounge.command.domain.event.LoungeChatRequestAccepted
import com.org.oneulsogae.core.lounge.command.domain.event.LoungeChatRequested
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserDetailUseCase
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 라운지 대화 신청·수락 도메인 이벤트의 후속 알람 처리를 한곳에서 다루는 핸들러.
 *
 * 알람은 부가 효과이므로 모두 커밋 이후(AFTER_COMMIT) 별도 트랜잭션([Propagation.REQUIRES_NEW])으로 best-effort 저장한다.
 * (알람 저장이 실패해도 신청·과금·채팅방은 롤백되지 않는다)
 * 알람 저장은 alarm 도메인 in-port([SaveAlarmUseCase])로 위임하고, 문구에 쓸 닉네임은 user 도메인 in-port([GetUserDetailUseCase])로 조회한다.
 */
@Component
class LoungeEventHandler(
	private val saveAlarmUseCase: SaveAlarmUseCase,
	private val getUserDetailUseCase: GetUserDetailUseCase,
) {

	/** 대화 신청 → 글 작성자에게 "대화 신청 받음" 알람. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun onLoungeChatRequested(event: LoungeChatRequested) {
		val requesterNickname: String? = getUserDetailUseCase.findByUserId(event.requesterUserId)?.nickname

		saveAlarmUseCase.save(
			SaveAlarmCommand(
				userId = event.postAuthorUserId,
				type = AlarmType.LOUNGE_CHAT_REQUEST_RECEIVED,
				title = "새로운 대화 신청",
				description = requesterNickname
					?.let { "${it}님이 회원님에게 대화를 신청했어요." }
					?: "회원님에게 대화를 신청한 상대가 있어요.",
				// 알람을 누르면 라운지로 이동한다. (프론트 라우팅에 맞춘 경로)
				link = "/",
				fromUserId = event.requesterUserId,
			),
		)
	}

	/** 대화 신청 수락 → 신청자에게 "대화 신청 수락됨" 알람. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun onLoungeChatRequestAccepted(event: LoungeChatRequestAccepted) {
		val authorNickname: String? = getUserDetailUseCase.findByUserId(event.postAuthorUserId)?.nickname

		saveAlarmUseCase.save(
			SaveAlarmCommand(
				userId = event.requesterUserId,
				type = AlarmType.LOUNGE_CHAT_ACCEPTED,
				title = "대화 신청 수락",
				description = authorNickname
					?.let { "${it}님이 대화 신청을 수락했어요." }
					?: "상대방이 대화 신청을 수락했어요.",
				// 알람을 누르면 생성된 채팅방으로 이동한다. (프론트 라우팅에 맞춘 경로)
				link = "/chat/${event.chatRoomId}",
				fromUserId = event.postAuthorUserId,
			),
		)
	}
}
```

> `GetUserDetailUseCase.findByUserId`의 반환 타입에 `nickname`이 없으면
> `oneulsogae-core/.../user/query/service/port/in/GetUserDetailUseCase.kt`를 읽어 실제 프로퍼티에 맞춘다.
> (`MatchEventHandler`가 같은 형태를 쓰고 있으므로 그대로 동작하는 것이 정상이다)

- [ ] **Step 5: 신청 서비스에서 이벤트를 발행한다**

`RequestLoungeChatService`에 임포트와 의존성을 더하고 발행 한 줄을 넣는다:

```kotlin
import com.org.oneulsogae.core.common.event.DomainEventPublisher
import com.org.oneulsogae.core.lounge.command.domain.event.LoungeChatRequested
```

```kotlin
class RequestLoungeChatService(
	private val getLoungePostPort: GetLoungePostPort,
	private val getLoungeChatRequestPort: GetLoungeChatRequestPort,
	private val saveLoungeChatRequestPort: SaveLoungeChatRequestPort,
	private val spendCoinUseCase: SpendCoinUseCase,
	private val domainEventPublisher: DomainEventPublisher,
) : RequestLoungeChatUseCase {
```

`spendCoinUseCase.spend(...)` 다음 줄에 넣는다:

```kotlin
		// 알람은 부가 효과라 커밋 후 별도 트랜잭션에서 best-effort로 처리한다. ([LoungeEventHandler])
		domainEventPublisher.publish(
			LoungeChatRequested(
				requestId = saved.id,
				requesterUserId = userId,
				postAuthorUserId = post.userId,
			),
		)
```

KDoc 마지막에 한 줄 더한다:

```
 * 알람만 커밋 후 best-effort([LoungeEventHandler])다.
```

- [ ] **Step 6: 수락 서비스에서 이벤트를 발행한다**

`AcceptLoungeChatService`에 임포트와 의존성을 더한다:

```kotlin
import com.org.oneulsogae.core.common.event.DomainEventPublisher
import com.org.oneulsogae.core.lounge.command.domain.event.LoungeChatRequestAccepted
```

```kotlin
	private val saveChatRoomUseCase: SaveChatRoomUseCase,
	private val domainEventPublisher: DomainEventPublisher,
) : AcceptLoungeChatUseCase {
```

`return AcceptLoungeChatResult(chatRoom.id)` 앞에 넣는다:

```kotlin
		// 알람은 부가 효과라 커밋 후 별도 트랜잭션에서 best-effort로 처리한다. ([LoungeEventHandler])
		domainEventPublisher.publish(
			LoungeChatRequestAccepted(
				requestId = request.id,
				requesterUserId = request.requesterUserId,
				postAuthorUserId = post.userId,
				chatRoomId = chatRoom.id,
			),
		)
		return AcceptLoungeChatResult(chatRoom.id)
```

KDoc 마지막에 한 줄 더한다:

```
 * 알람만 커밋 후 best-effort([LoungeEventHandler])다.
```

- [ ] **Step 7: 테스트를 실행해 통과를 확인한다**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.lounge.LoungeChatRequestAlarmE2ETest"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: 라운지 전체 테스트를 돌린다**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.lounge.*" --tests "com.org.oneulsogae.domain.lounge.*"`
Expected: BUILD SUCCESSFUL — 신청·목록·수락·알람·기존 셀소 테스트 모두 통과

- [ ] **Step 9: 전체 빌드로 회귀를 확인한다**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: 커밋**

```bash
git add oneulsogae-core oneulsogae-api
git commit -m "feat(lounge): 대화 신청·수락 알람 추가"
```

---

## 완료 후 사용자에게 안내할 프론트엔드 변경 (백엔드에서 수정하지 않음)

`meeple-frontend`에서 다음이 필요하다:

1. **셀소 상세**: "대화 신청" 버튼 → `POST /lounge/v1/self-intro-posts/{postId}/chat-requests`. 코인 32 소모 안내, 400(LOUNGE-009)·409(LOUNGE-010)·코인 부족 처리.
2. **내 셀소 신청자 목록**: `GET /lounge/v1/self-intro-posts/{postId}/chat-requests` — `items[].{requestId, userId, nickname, gender, age, status, chatRoomId, requestedAt}`, `hasNext`/`nextCursor` 커서 페이징. `status`가 `PENDING`이면 "수락" 버튼, `ACCEPTED`면 `chatRoomId`로 채팅방 이동.
3. **수락**: `POST /lounge/v1/chat-requests/{requestId}/accept` → `data.chatRoomId`로 이동.
4. **알람 목록**: `AlarmType`에 `LOUNGE_CHAT_REQUEST_RECEIVED`, `LOUNGE_CHAT_ACCEPTED` 문구·아이콘 추가. 알림 설정 토글은 기존 "1:1 소개"가 관장하므로 마이탭 변경 없음.
5. **채팅방 목록/상세**: 채팅방 `type`에 `LOUNGE`가 추가된다. `SOLO`와 동일하게 1:1 사용자 방으로 렌더링하면 된다.
