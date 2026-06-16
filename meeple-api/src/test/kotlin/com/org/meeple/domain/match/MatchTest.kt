package com.org.meeple.domain.match

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.core.fixture.MatchFixture
import com.org.meeple.core.match.domain.Match
import com.org.meeple.core.match.domain.event.InterestSent
import com.org.meeple.core.match.domain.event.MatchAccepted
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [Match] 도메인 유닛 테스트.
 * 프레임워크·인프라 없이 순수 도메인 로직(응답 시 상태/만료 전이, 성사 이벤트 변환)을 검증한다.
 * 참가자/수락은 [MatchMember]가 보관하므로 [MatchFixture.membersOf]로 1:1 남/녀를 구성한다.
 */
class MatchTest : DescribeSpec({

	val maleUserId: Long = 1L
	val femaleUserId: Long = 2L

	fun proposedMatch(id: Long = 0): Match =
		MatchFixture.create(id = id, members = MatchFixture.membersOf(maleUserId = maleUserId, femaleUserId = femaleUserId))

	describe("respond - 성사 시 만료 연장") {
		it("양쪽이 수락해 MATCHED가 되면 만료 시각을 100년 뒤로 미룬다") {
			val proposed: Match = proposedMatch()

			val matched: Match = proposed.respond(maleUserId).respond(femaleUserId)

			matched.status shouldBe MatchStatus.MATCHED
			matched.expiresAt shouldBe proposed.expiresAt.plusYears(Match.MATCHED_EXPIRATION_EXTENSION_YEARS)
		}

		it("한쪽만 수락해 PARTIALLY_ACCEPTED면 만료 시각을 유지한다") {
			val proposed: Match = proposedMatch()

			val partial: Match = proposed.respond(maleUserId)

			partial.status shouldBe MatchStatus.PARTIALLY_ACCEPTED
			partial.expiresAt shouldBe proposed.expiresAt
		}
	}

	describe("respond - 관심/상대 관심 집계") {
		it("내가 수락하면 hasUserInterest=true, 상대 미응답이면 hasPartnerInterest=false") {
			val responded: Match = proposedMatch().respond(maleUserId)

			responded.hasUserInterest(maleUserId) shouldBe true
			responded.hasPartnerInterest(maleUserId) shouldBe false
		}
	}

	describe("MatchAccepted.from") {
		it("성사된 매칭의 id·수락자·상대를 이벤트로 옮긴다") {
			val matched: Match = MatchFixture.create(
				id = 7L,
				members = MatchFixture.membersOf(maleUserId = maleUserId, femaleUserId = femaleUserId),
				status = MatchStatus.MATCHED,
			)

			val event: MatchAccepted = MatchAccepted.from(matched, acceptedByUserId = maleUserId)

			// 수락자(남)의 상대(= 알람 수신자)는 여성이다.
			event shouldBe MatchAccepted(matchId = 7L, acceptedByUserId = maleUserId, partnerOfAcceptor = femaleUserId)
		}
	}

	describe("InterestSent.from") {
		it("보낸 사람의 상대를 수신자로 하는 이벤트를 만든다") {
			val match: Match = proposedMatch(id = 7L)

			val event: InterestSent = InterestSent.from(match, senderUserId = maleUserId)

			event shouldBe InterestSent(matchId = 7L, senderUserId = maleUserId, recipientUserId = femaleUserId)
		}
	}
})
