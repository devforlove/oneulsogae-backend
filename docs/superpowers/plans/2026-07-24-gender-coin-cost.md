# 남녀별 코인 비용 분리 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 코인 차감 비용을 남녀별로 분리 — 남자는 기존 금액, 여자는 절반. 환불은 실차감액 스냅샷 기반.

**Architecture:** `CoinUsageType`에 성별별 금액(`coinAmount(gender)`)을 추가하고, 5개 차감 지점을 행위자 성별 계산으로 전환한다. 환불은 신청 시 지불액을 참가 레코드에 스냅샷(nullable 컬럼 3개, 구행은 기존 값 fallback). 표시 5곳은 뷰어 성별 기준으로 계산(미상이면 null). 마지막에 구 `coinAmount` 프로퍼티를 제거해 잔여 사용처를 컴파일로 강제 검출한다.

**Tech Stack:** Kotlin/Spring(헥사고날), Kotest(유닛·E2E/Testcontainers), Next.js(웹 약관 1곳).

**스펙:** `docs/superpowers/specs/2026-07-24-gender-coin-cost-design.md` (2026-07-24 교정판 — 환불 설계는 교정판이 기준)

## Global Constraints

- 금액표 (남/여): DATING_INIT 32/16 · DATING_ACCEPT 32/16 · MEETING_INIT 40/20 · MEETING_ACCEPT 40/20 · EXTRA_INTRO 30/15 · LOUNGE_CHAT_INIT 32/16 · LOUNGE_CHAT_ACCEPT 32/16.
- **환불 = 실제 차감액의 절반**. 신청 시 지불액 스냅샷(nullable), 구행(null)은 기존 헤더 스냅샷/정책값 fallback.
- 차감 경로 성별 null이면 남자 금액 fallback. 표시 경로는 성별 미상이면 **null**(비용 숨김).
- enum 이름·`description` 불변(DB `coin_usage_type` 호환). 헤더 `datingInitAmount`/`dateInitAmount`/`dateAcceptAmount` 필드는 fallback 용도로 유지(제거 금지).
- 타입 명시, 탭 들여쓰기, 한국어 KDoc, 도메인 로직 서비스 인라인 금지(도메인 메서드로 캡슐화), 무관 변경 금지.
- 커밋 형식 `<type>(<domain>): <설명>` + 트레일러 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- **Task 1~5 동안 구 `coinAmount` 프로퍼티는 유지**(전 리포 컴파일 유지). 제거는 Task 6에서.
- 기존 E2E가 고정 금액(32 등)을 단언하는 곳은 새 정책 기대값으로 갱신(픽스처 성별 확인 필수).
- 운영 DDL 3건(계획 마지막 참고)은 최종 보고에 포함.

---

### Task 1: enum 성별별 금액 + 유닛 테스트

**Files:**
- Modify: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/coin/CoinUsageType.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/coin/CoinUsageTypeTest.kt` (신규)

**Interfaces:**
- Produces: `CoinUsageType.coinAmount(gender: Gender?): Int` — FEMALE이면 절반값, MALE·null이면 기존값. **기존 `val coinAmount: Int`는 이번 태스크에서 유지**(Task 6에서 제거).

- [ ] **Step 1: 실패하는 유닛 테스트 작성** (같은 디렉토리 기존 테스트의 Kotest 스타일 확인 후 동일하게)

```kotlin
package com.org.oneulsogae.domain.coin

import com.org.oneulsogae.common.coin.CoinUsageType
import com.org.oneulsogae.common.user.Gender
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class CoinUsageTypeTest : DescribeSpec({

	describe("coinAmount(gender)") {
		it("남성은 기존 금액을 그대로 낸다") {
			CoinUsageType.DATING_INIT.coinAmount(Gender.MALE) shouldBe 32
			CoinUsageType.MEETING_INIT.coinAmount(Gender.MALE) shouldBe 40
			CoinUsageType.DATING_ACCEPT.coinAmount(Gender.MALE) shouldBe 32
			CoinUsageType.MEETING_ACCEPT.coinAmount(Gender.MALE) shouldBe 40
			CoinUsageType.EXTRA_INTRO.coinAmount(Gender.MALE) shouldBe 30
			CoinUsageType.LOUNGE_CHAT_INIT.coinAmount(Gender.MALE) shouldBe 32
			CoinUsageType.LOUNGE_CHAT_ACCEPT.coinAmount(Gender.MALE) shouldBe 32
		}

		it("여성은 전부 절반을 낸다") {
			CoinUsageType.DATING_INIT.coinAmount(Gender.FEMALE) shouldBe 16
			CoinUsageType.MEETING_INIT.coinAmount(Gender.FEMALE) shouldBe 20
			CoinUsageType.DATING_ACCEPT.coinAmount(Gender.FEMALE) shouldBe 16
			CoinUsageType.MEETING_ACCEPT.coinAmount(Gender.FEMALE) shouldBe 20
			CoinUsageType.EXTRA_INTRO.coinAmount(Gender.FEMALE) shouldBe 15
			CoinUsageType.LOUNGE_CHAT_INIT.coinAmount(Gender.FEMALE) shouldBe 16
			CoinUsageType.LOUNGE_CHAT_ACCEPT.coinAmount(Gender.FEMALE) shouldBe 16
		}

		it("성별 미상(null)은 남성 금액으로 fallback한다") {
			CoinUsageType.DATING_INIT.coinAmount(null) shouldBe 32
		}
	}
})
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests 'com.org.oneulsogae.domain.coin.CoinUsageTypeTest'`
Expected: 컴파일 실패 (coinAmount(Gender) 미존재)

- [ ] **Step 3: 구현**

`CoinUsageType.kt` 전체 교체:

```kotlin
package com.org.oneulsogae.common.coin

import com.org.oneulsogae.common.user.Gender

/**
 * 코인을 사용(차감)하는 작업의 유형.
 * 소개팅(DATING)/미팅(MEETING) 각각에 대해 신청(INIT)과 수락(ACCEPT)을 구분한다.
 * 비용은 남녀가 다르다 — 남성은 [coinAmount], 여성은 그 절반([femaleAmount]). [coinAmount(Gender?)]로 얻는다.
 */
enum class CoinUsageType(val description: String, val coinAmount: Int, private val femaleAmount: Int) {

	/** 소개팅 신청. */
	DATING_INIT("소개팅 신청", 32, 16),

	/** 미팅 신청. */
	MEETING_INIT("미팅 신청", 40, 20),

	/** 소개팅 수락. */
	DATING_ACCEPT("소개팅 수락", 32, 16),

	/** 미팅 수락. */
	MEETING_ACCEPT("미팅 수락", 40, 20),

	/** 추가 소개(오늘의 추천 외 1명 더 소개받기). */
	EXTRA_INTRO("추가 소개", 30, 15),

	/** 라운지 셀소 대화 신청. */
	LOUNGE_CHAT_INIT("셀소 대화 신청", 32, 16),

	/** 라운지 셀소 대화 수락. */
	LOUNGE_CHAT_ACCEPT("셀소 대화 수락", 32, 16),
	;

	/** 성별별 비용. 여성은 절반, 남성·미상(null)은 기존 금액. (차감 경로의 null은 이론상 없고 fallback 안전장치) */
	fun coinAmount(gender: Gender?): Int =
		if (gender == Gender.FEMALE) femaleAmount else coinAmount
}
```

(주의: `val coinAmount: Int`는 유지 — 기존 사용처가 Task 2~5에서 순차 이관되고 Task 6에서 제거된다.)

- [ ] **Step 4: 테스트 통과 + 전 모듈 컴파일 확인**

Run: `./gradlew :oneulsogae-api:test --tests 'com.org.oneulsogae.domain.coin.CoinUsageTypeTest' && ./gradlew compileKotlin :oneulsogae-api:compileKotlin`
Expected: PASS + BUILD SUCCESSFUL

- [ ] **Step 5: 커밋** — `feat(coin): CoinUsageType에 남녀별 비용(coinAmount(gender)) 추가`

---

### Task 2: solomatch — 차감·환불·표시

**Files:**
- Modify: `oneulsogae-core/.../core/solomatch/command/domain/Match.kt`, `MatchMember.kt`, `MatchMembers.kt`
- Modify: `oneulsogae-infra/.../infra/solomatch/command/entity/SoloMatchMemberEntity.kt`, `mapper/MatchMemberMapper.kt`
- Modify: `oneulsogae-core/.../core/solomatch/command/application/SendInterestService.kt`
- Modify: `oneulsogae-api/.../api/match/response/MatchResponse.kt` + 이를 채우는 조회 경로(컨트롤러/서비스 — `MatchResponse.of` 호출부를 grep으로 찾아 뷰어 성별 전달)
- Test: 기존 solomatch E2E(관심 보내기·만료 환불) 갱신 + 여성 차감 케이스, `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/` 유닛(있으면 갱신)

**Interfaces:**
- Consumes: Task 1 `coinAmount(gender: Gender?): Int`
- Produces (도메인 메서드, 시그니처 고정):
  - `Match.datingInitAmountFor(userId: Long): Int` — 해당 참가자 성별 기준 신청 비용
  - `Match.datingAcceptAmountFor(userId: Long): Int` — 해당 참가자 성별 기준 수락 비용
  - `MatchMember.paidInitAmount: Int?` — 신청 시 실제 지불액 스냅샷 (구행 null)
  - `Match.respond(userId)`가 APPLY 전이 시 `paidInitAmount = CoinUsageType.DATING_INIT.coinAmount(member.gender)` 기록
  - `Match.failureRefunds()` = 환불 대상 member의 `(paidInitAmount ?: datingInitAmount) / 2`

- [ ] **Step 1: 파일 정독** — `MatchMember.kt`/`MatchMembers.kt`를 읽고 `apply(userId)` 전이 지점 확인. `SoloMatchMemberEntity` 컬럼 구조·`@Table` 이름 확인(운영 DDL 테이블명 확정).

- [ ] **Step 2: 실패하는 유닛 테스트** — `domain/match`(또는 solomatch 기존 유닛 위치)에 추가. 핵심 케이스:

```kotlin
// 여성 멤버가 신청하면 datingInitAmountFor가 16, 남성은 32
// respond 후 해당 member의 paidInitAmount가 성별 금액으로 기록됨
// failureRefunds: paidInitAmount=16이면 8 환불, paidInitAmount=null(구행)이면 datingInitAmount(32)/2=16
```

(Match/MatchMembers 픽스처 생성 방식은 기존 유닛 테스트 파일을 따라 작성. 없으면 `Match.propose(...)`로 직접 조립.)

- [ ] **Step 3: 도메인 구현**

`MatchMember`에 `val paidInitAmount: Int? = null` 추가(전이 함수 copy 유지 확인). `MatchMembers.apply(userId)`가 APPLY 전이 시 지불액을 함께 기록하도록 수정 — 금액 계산은 member.gender 기반:

```kotlin
// MatchMembers 내 (기존 apply 시그니처 유지 가능하면 내부에서 계산)
fun apply(userId: Long): MatchMembers = ... // APPLY 전이 + paidInitAmount = CoinUsageType.DATING_INIT.coinAmount(member.gender)
```

`Match`에 메서드 추가:

```kotlin
	/** 해당 참가자의 성별 기준 소개팅 신청 비용. (남 32 / 여 16) */
	fun datingInitAmountFor(userId: Long): Int =
		CoinUsageType.DATING_INIT.coinAmount(members.find(userId)?.gender)

	/** 해당 참가자의 성별 기준 소개팅 수락 비용. (남 32 / 여 16) */
	fun datingAcceptAmountFor(userId: Long): Int =
		CoinUsageType.DATING_ACCEPT.coinAmount(members.find(userId)?.gender)
```

`failureRefunds()` 수정:

```kotlin
	fun failureRefunds(): List<MatchRefund> =
		members.refundableMembers()
			.map { member: MatchMember -> MatchRefund(userId = member.userId, amount = (member.paidInitAmount ?: datingInitAmount) / 2) }
			.filter { refund: MatchRefund -> refund.amount > 0 }
```

KDoc도 실차감액 기준으로 갱신.

- [ ] **Step 4: 인프라 반영** — `SoloMatchMemberEntity`에 `@Column(name = "paid_init_amount") var paidInitAmount: Int? = null`, `MatchMemberMapper` 양방향 반영.

- [ ] **Step 5: 차감 전환** — `SendInterestService`:

```kotlin
		// completeMatch 내
		spend(userId, amount = match.datingAcceptAmountFor(userId), usageType = CoinUsageType.DATING_ACCEPT)
		// recordInterest 내
		spend(userId, amount = match.datingInitAmountFor(userId), usageType = CoinUsageType.DATING_INIT)
```

- [ ] **Step 6: 표시 전환** — `MatchResponse.of` 호출부(grep `MatchResponse.of`)를 찾아 뷰어 성별을 전달, `datingInitAmount = CoinUsageType.DATING_INIT.coinAmount(viewerGender)` / accept 동일하게 계산. 뷰어 성별은 해당 조회 경로가 이미 가진 데이터(참가자 read model에 gender 있으면 그것) 우선, 없으면 `GetUserDetailUseCase`. 조회 서비스가 채우고 컨트롤러는 그대로.

- [ ] **Step 7: 테스트** — 유닛 통과 + 관련 E2E(관심 보내기/만료 환불 — grep `SendInterest`·`ExpireSoloMatch` E2E) 실행. 여성 신청 16 차감 E2E 케이스 1개 추가(기존 관심 보내기 E2E에 context 추가, 픽스처 성별 FEMALE). 기존 단언 32가 픽스처 성별과 안 맞으면 기대값 갱신.

Run: `./gradlew :oneulsogae-api:test --tests '*Interest*' --tests '*ExpireSolo*' --tests '*Match*'` (실제 클래스명으로 조정)

- [ ] **Step 8: 커밋** — `feat(solomatch): 소개팅 신청·수락 비용 남녀 분리, 환불은 실차감액 스냅샷 기반`

---

### Task 3: teammatch — 차감·환불·표시

**Files:**
- Modify: `oneulsogae-core/.../teammatch/command/domain/TeamMatch.kt`, `MatchedTeam.kt`, `MatchedTeams.kt`
- Modify: `oneulsogae-infra/.../teammatch/command/entity/MatchedTeamEntity.kt`, `mapper/MatchedTeamMapper.kt`
- Modify: `oneulsogae-core/.../teammatch/command/application/SendTeamInterestService.kt`
- Modify: `oneulsogae-infra/.../teammatch/query/GetRecommendedTeamDaoImpl.kt` + 그 dao 인터페이스·호출 서비스(뷰어 성별 파라미터)
- Test: 팀 관심/만료 E2E 갱신 + 여성팀 20 차감 케이스

**Interfaces:**
- Consumes: Task 1.
- Produces:
  - `TeamMatch.respond(teamId: Long, applicantUserId: Long, paidInitAmount: Int): TeamMatch` — APPLY 전이 시 지불액 스냅샷 기록 (성별은 도메인이 모름 — 서비스가 `MEETING_INIT.coinAmount(actorTeam.gender)`를 전달)
  - `MatchedTeam.paidInitAmount: Int?`
  - `TeamMatch.failureRefunds()` = `(paidInitAmount ?: dateInitAmount) / 2`

- [ ] **Step 1: 파일 정독** — `MatchedTeam.kt`/`MatchedTeams.kt`의 `apply(teamId, applicantUserId)` 확인, `MatchedTeamEntity` `@Table` 이름 확인(운영 DDL 확정).

- [ ] **Step 2: 유닛 테스트(실패)** — respond 시 paidInitAmount 기록, failureRefunds 스냅샷/2·구행 fallback `dateInitAmount/2`.

- [ ] **Step 3: 도메인 구현** — `MatchedTeam.paidInitAmount: Int? = null`, `MatchedTeams.apply(teamId, applicantUserId, paidInitAmount)` 확장, `TeamMatch.respond` 시그니처에 `paidInitAmount: Int` 추가(KDoc 갱신), `failureRefunds`:

```kotlin
	fun failureRefunds(): List<MatchRefund> =
		matchedTeams.refundableTeams()
			.mapNotNull { team: MatchedTeam -> team.applicantUserId?.let { userId: Long -> MatchRefund(userId = userId, amount = (team.paidInitAmount ?: dateInitAmount) / 2) } }
			.filter { refund: MatchRefund -> refund.amount > 0 }
```

- [ ] **Step 4: 인프라 반영** — `MatchedTeamEntity.paidInitAmount`(`paid_init_amount`, nullable) + 매퍼 양방향.

- [ ] **Step 5: 차감 전환** — `SendTeamInterestService.sendInterest`:

```kotlin
		val updated: TeamMatch = saveTeamMatchPort.save(
			teamMatch.respond(actorTeam.id, userId, paidInitAmount = CoinUsageType.MEETING_INIT.coinAmount(actorTeam.gender)),
		)
```

completeMatch/recordInterest의 spend 금액을 행위자 팀 성별로 전환 — completeMatch에는 actorTeam이 없으므로 `teams.findByActiveMember(userId)`로 재도출하거나 completeMatch 시그니처에 actorTeam 전달(후자 권장, recordInterest와 대칭):

```kotlin
		// completeMatch(userId, updated, actorTeam, teams) 로 변경
		spend(userId, amount = CoinUsageType.MEETING_ACCEPT.coinAmount(actorTeam.gender), usageType = CoinUsageType.MEETING_ACCEPT)
		// recordInterest 내
		spend(userId, amount = CoinUsageType.MEETING_INIT.coinAmount(actorTeam.gender), usageType = CoinUsageType.MEETING_INIT)
```

- [ ] **Step 6: 표시 전환** — `GetRecommendedTeamDaoImpl`의 고정값 두 줄을 뷰어 성별 계산으로: dao 인터페이스(`core/teammatch/query` 쪽 port — grep으로 확인)에 `viewerGender: Gender?` 파라미터 추가, 호출 서비스가 뷰어 성별 조회·전달(기존 로드 데이터에 있으면 재사용). `RecommendedTeamResponse`는 read model 값을 그대로 쓰므로 추가 수정 없음.

- [ ] **Step 7: 테스트** — 유닛 + 팀 관심/만료 E2E 갱신, 여성팀 신청 20 차감 케이스 추가.

- [ ] **Step 8: 커밋** — `feat(teammatch): 미팅 신청·수락 비용 남녀(팀 성별) 분리, 환불은 실차감액 스냅샷 기반`

---

### Task 4: lounge — 차감·환불·표시

**Files:**
- Modify: `oneulsogae-core/.../lounge/command/domain/LoungeChatRequest.kt`
- Modify: `oneulsogae-infra/.../lounge/command/entity/LoungeChatRequestEntity.kt`, `mapper/LoungeChatRequestMapper.kt`
- Modify: `oneulsogae-core/.../lounge/command/application/RequestLoungeChatService.kt`, `AcceptLoungeChatService.kt`
- Modify: `oneulsogae-core/.../lounge/query/dto/LoungeChatRequestPage.kt`, `SelfIntroPostDetailView.kt` + 각각을 채우는 query 서비스(뷰어 성별 전달 — grep으로 확인)
- Test: 라운지 신청/수락/만료 E2E 갱신 + 여성 16 차감·비로그인 null 케이스

**Interfaces:**
- Consumes: Task 1.
- Produces:
  - `LoungeChatRequest.initCoinAmount: Int?` — 신청 시 실제 차감액 스냅샷 (구행 null)
  - `LoungeChatRequest.create(..., initCoinAmount: Int, ...)` — 생성 시 기록
  - `LoungeChatRequest.expiryRefundAmount(): Int` = `(initCoinAmount ?: CoinUsageType.LOUNGE_CHAT_INIT.coinAmount) / 2`
  - `SelfIntroPostDetailView.chatRequestCoinAmount: Int?` (nullable 전환)

- [ ] **Step 1: 도메인** — `LoungeChatRequest`에 `val initCoinAmount: Int? = null` 추가, `create()` 파라미터에 `initCoinAmount: Int` 추가해 기록, `expiryRefundAmount()`:

```kotlin
	/** 만료 정리 시 신청자에게 되돌려줄 코인 — 실제 낸 신청 비용([initCoinAmount])의 절반(내림). 구행(null)은 기존 정책값 기준. */
	fun expiryRefundAmount(): Int =
		(initCoinAmount ?: CoinUsageType.LOUNGE_CHAT_INIT.coinAmount) / 2
```

유닛 테스트(기존 lounge 도메인 테스트 위치): initCoinAmount=16 → 환불 8, null → 16.

- [ ] **Step 2: 인프라** — `LoungeChatRequestEntity`에 `@Column(name = "init_coin_amount") var initCoinAmount: Int? = null` + 매퍼 양방향. `@Table` 이름 확인.

- [ ] **Step 3: 신청 차감 전환** — `RequestLoungeChatService`:

```kotlin
		val initCoinAmount: Int = USAGE_TYPE.coinAmount(requesterDetail?.gender)
		val saved: LoungeChatRequest = saveLoungeChatRequestPort.save(
			LoungeChatRequest.create(..., initCoinAmount = initCoinAmount, ...),
		)
		spendCoinUseCase.spend(userId, SpendCoinCommand(amount = initCoinAmount, coinUsageType = USAGE_TYPE))
```

- [ ] **Step 4: 수락 차감 전환** — `AcceptLoungeChatService`에 `GetUserDetailUseCase` 주입 추가:

```kotlin
		val actorGender: Gender? = getUserDetailUseCase.findByUserId(userId)?.gender
		spendCoinUseCase.spend(userId, SpendCoinCommand(amount = USAGE_TYPE.coinAmount(actorGender), coinUsageType = USAGE_TYPE))
```

- [ ] **Step 5: 표시 전환** — `LoungeChatRequestPage.acceptCoinAmount`를 뷰어(글 작성자) 성별 계산으로(query 서비스가 성별 조회·전달), `SelfIntroPostDetailView.chatRequestCoinAmount`를 `Int?`로 바꾸고 뷰어 userId null(비로그인)이면 null, 아니면 뷰어 성별 계산. 채우는 서비스(`GetSelfIntroPostsService` 등)는 grep으로 확인.

- [ ] **Step 6: 테스트** — 라운지 E2E(신청/수락/만료 환불) 갱신 + 여성 신청 16 차감·만료 환불 8, 비로그인 상세 조회 chatRequestCoinAmount null 케이스.

- [ ] **Step 7: 커밋** — `feat(lounge): 셀소 대화 신청·수락 비용 남녀 분리, 만기 환불은 실차감액 스냅샷 기반`

---

### Task 5: 추가 소개 — 차감·표시

**Files:**
- Modify: `oneulsogae-core/.../solomatch/command/application/IntroduceExtraMatchService.kt`
- Modify: `oneulsogae-api/.../api/match/response/ExtraIntroCandidatesResponse.kt` + `of()` 호출 컨트롤러/서비스
- Test: 추가 소개 E2E 갱신 + 여성 15 차감·coinCost 15 표시

**Interfaces:**
- Consumes: Task 1. `requester: MatchUser`(gender 보유)는 서비스에 이미 로드됨.
- Produces: `ExtraIntroCandidatesResponse.of(result, today, viewerGender: Gender?)` — `coinCost = CoinUsageType.EXTRA_INTRO.coinAmount(viewerGender)`.

- [ ] **Step 1: 차감 전환** — `IntroduceExtraMatchService`의 spend 금액을 `CoinUsageType.EXTRA_INTRO.coinAmount(requester.gender)`로.

- [ ] **Step 2: 표시 전환** — `ExtraIntroCandidatesResponse.of`에 `viewerGender: Gender?` 파라미터 추가해 계산. 호출부(grep `ExtraIntroCandidatesResponse.of`)가 요청자 성별을 전달 — 후보 조회 결과(result)에 요청자 성별이 있으면 재사용, 없으면 해당 서비스가 이미 로드하는 MatchUser/UserDetail에서.

- [ ] **Step 3: 테스트** — 추가 소개 E2E: 여성 요청자 15 차감 + 응답 coinCost 15, 남성 30/30 회귀.

- [ ] **Step 4: 커밋** — `feat(solomatch): 추가 소개 비용 남녀 분리 (차감·표시)`

---

### Task 6: 구 프로퍼티 제거 + 웹 약관

**Files:**
- Modify: `oneulsogae-common/.../coin/CoinUsageType.kt` (`val coinAmount` 제거 → `coinAmount(gender)` 단일화)
- Modify: 제거로 컴파일 에러 나는 잔여 사용처 전부 (예상: `TeamMatch`/`Match` propose 기본값 — `coinAmount(null)`로 교체, 테스트 픽스처 등)
- Modify (웹, `/Users/inwookjung/IdeaProjects/oneulsogae-frontend`): `src/domains/wallet/domain/entities/Coin.ts`(`LIKE_COST` 제거 또는 남녀 상수화), `src/domains/legal/presentation/components/TermsPage.tsx:198`(관심 보내기 비용 문구 남/녀 병기)
- Test: 백엔드 전체 테스트

**Interfaces:**
- Consumes: Task 1~5 완료 상태 (구 프로퍼티 사용처가 도메인 fallback·propose 기본값만 남음).

- [ ] **Step 1: 프로퍼티 제거** — `val coinAmount: Int` → private 생성자 파라미터로 변경(`private val maleAmount: Int`로 rename, `coinAmount(gender)` 내부에서 사용). 컴파일 에러 지점 전부 `coinAmount(null)` 또는 적절한 성별 인자로 교체. `grep -rn '\.coinAmount[^(]'`로 잔여 확인(0건이어야 함).

- [ ] **Step 2: 백엔드 전체 테스트**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, 0 failures

- [ ] **Step 3: 커밋(백엔드)** — `refactor(coin): CoinUsageType 단일 금액 프로퍼티 제거, 성별별 함수로 일원화`

- [ ] **Step 4: 웹 약관 수정** — `TermsPage.tsx:198` 문구를 남/녀 병기로:

```
관심 보내기 1회에는 남성 32코인, 여성 16코인이 소모됩니다.
```

`Coin.ts`의 `LIKE_COST`는 사용처가 약관뿐이므로 제거하고 약관에 리터럴 병기(또는 `LIKE_COST_MALE=32`/`LIKE_COST_FEMALE=16` 상수화 — 파일 컨벤션에 맞게). 웹 셀소 상세의 `chatRequestCoinAmount` null(비로그인) 처리 확인 — `SelfIntroDetail.tsx:153` 인근에서 null이면 비용 문구 숨김/로그인 유도인지 확인, "undefined코인" 노출이면 방어 추가.

Run: `npx tsc --noEmit && npm run build` (frontend 루트)

- [ ] **Step 5: 커밋(웹)** — `fix(legal): 약관 관심 보내기 비용을 남녀 병기로 갱신`

---

## 최종 확인

- [ ] 백엔드 `./gradlew test` 전체 통과
- [ ] `grep -rn 'coinAmount[^(]' --include='*.kt'` 잔여 0건 (프로퍼티 참조 없음)
- [ ] 운영 DDL 3건 보고 (테이블명은 Task 2·3·4에서 확정한 실명으로):

```sql
ALTER TABLE <solo_match_members> ADD COLUMN paid_init_amount INT NULL;
ALTER TABLE <matched_teams> ADD COLUMN paid_init_amount INT NULL;
ALTER TABLE <lounge_chat_requests> ADD COLUMN init_coin_amount INT NULL;
```

- [ ] 배포 순서: DDL → 백엔드 → 웹(약관). 모바일은 서버값 기반이라 배포 불필요.
