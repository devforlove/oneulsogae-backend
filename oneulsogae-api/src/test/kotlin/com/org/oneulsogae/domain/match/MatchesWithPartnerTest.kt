package com.org.oneulsogae.domain.match

import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.core.solomatch.query.dto.MatchWithPartner
import com.org.oneulsogae.core.solomatch.query.dto.MatchesWithPartner
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [MatchesWithPartner] 일급 컬렉션 유닛 테스트.
 * 매칭 목록 노출 순서 규칙(상태 우선순위 → 같은 상태는 최신순)을 검증한다.
 */
class MatchesWithPartnerTest : DescribeSpec({

	fun match(matchId: Long, status: MatchStatus): MatchWithPartner =
		MatchWithPartner(
			matchId = matchId,
			status = status,
			expiresAt = LocalDateTime.of(2026, 7, 3, 12, 0),
			datingInitAmount = 10,
			datingAcceptAmount = 10,
			hasUserInterest = false,
			hasPartnerInterest = false,
			checkedAt = null,
			partnerUserId = 100L + matchId,
			nickname = null,
			profileImageCode = null,
			birthday = null,
			height = null,
			gender = null,
			job = null,
			activityArea = null,
			introduction = null,
			companyName = null,
			universityName = null,
			traits = emptyList(),
			interests = emptyList(),
			maritalStatus = null,
			smokingStatus = null,
			religion = null,
			drinkingStatus = null,
			bodyType = null,
			lastLoginAt = null,
		)

	describe("sortedForDisplay") {

		it("성사(MATCHED) → 상대 수락 대기(PARTIALLY_ACCEPTED) → 소개됨(PROPOSED) 순으로 정렬한다") {
			val matches = MatchesWithPartner(
				listOf(
					match(1L, MatchStatus.PROPOSED),
					match(2L, MatchStatus.MATCHED),
					match(3L, MatchStatus.PARTIALLY_ACCEPTED),
				),
			)

			matches.sortedForDisplay().map { it.matchId } shouldBe listOf(2L, 3L, 1L)
		}

		it("같은 상태 안에서는 최신(matchId 내림차순)순으로 정렬한다") {
			val matches = MatchesWithPartner(
				listOf(
					match(1L, MatchStatus.PROPOSED),
					match(3L, MatchStatus.PROPOSED),
					match(2L, MatchStatus.PROPOSED),
				),
			)

			matches.sortedForDisplay().map { it.matchId } shouldBe listOf(3L, 2L, 1L)
		}
	}
})
