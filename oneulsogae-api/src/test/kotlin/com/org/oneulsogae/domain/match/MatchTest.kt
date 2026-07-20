package com.org.oneulsogae.domain.match

import com.org.oneulsogae.common.match.MatchMemberStatus
import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.fixture.MatchFixture
import com.org.oneulsogae.core.solomatch.MatchErrorCode
import com.org.oneulsogae.core.solomatch.command.domain.Match
import com.org.oneulsogae.core.solomatch.command.domain.MatchRefund
import com.org.oneulsogae.core.solomatch.command.domain.event.InterestSent
import com.org.oneulsogae.core.solomatch.command.domain.event.MatchAccepted
import com.org.oneulsogae.core.solomatch.command.domain.event.MatchEnded
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

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
		it("양쪽이 수락해 MATCHED가 되면 만료 시각을 100년 뒤로 미루고 전원 ACTIVE로 승격한다") {
			val proposed: Match = proposedMatch()

			val matched: Match = proposed.respond(maleUserId).respond(femaleUserId)

			matched.status shouldBe MatchStatus.MATCHED
			matched.expiresAt shouldBe proposed.expiresAt.plusYears(Match.MATCHED_EXPIRATION_EXTENSION_YEARS)
			matched.members.values.all { it.status == MatchMemberStatus.ACTIVE } shouldBe true
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

	describe("failureRefunds - 미성사 만료 환불 산정") {
		it("신청(APPLY)했으나 미성사인 참가자에게만 신청 비용의 절반을 환불한다") {
			val partiallyAccepted: Match = MatchFixture.create(
				members = MatchFixture.membersOf(
					maleUserId = maleUserId, femaleUserId = femaleUserId,
					maleStatus = MatchMemberStatus.APPLY, femaleStatus = MatchMemberStatus.WAITING,
				),
				status = MatchStatus.PARTIALLY_ACCEPTED,
			)

			val refunds: List<MatchRefund> = partiallyAccepted.failureRefunds()

			refunds.size shouldBe 1
			refunds.first().userId shouldBe maleUserId
			refunds.first().amount shouldBe partiallyAccepted.datingInitAmount / 2
		}

		it("성사(MATCHED)되어 전원 ACTIVE면 환불 대상이 없다") {
			val matched: Match = MatchFixture.create(
				members = MatchFixture.membersOf(
					maleUserId = maleUserId, femaleUserId = femaleUserId,
					maleStatus = MatchMemberStatus.ACTIVE, femaleStatus = MatchMemberStatus.ACTIVE,
				),
				status = MatchStatus.MATCHED,
			)

			matched.failureRefunds() shouldBe emptyList()
		}

		it("아무도 신청하지 않았으면 환불 대상이 없다") {
			proposedMatch().failureRefunds() shouldBe emptyList()
		}
	}

	describe("delete - 제거 시 종료/소프트삭제") {
		it("헤더는 CLOSED, 참가자는 DEACTIVE로 바꾸고 헤더·참가자에 deletedAt을 채운다") {
			val now: LocalDateTime = LocalDateTime.of(2026, 6, 17, 12, 0)

			val deleted: Match = proposedMatch(id = 7L).delete(now)

			deleted.status shouldBe MatchStatus.CLOSED
			deleted.deletedAt shouldBe now
			deleted.members.values.all { it.status == MatchMemberStatus.DEACTIVE } shouldBe true
			deleted.members.values.all { it.deletedAt == now } shouldBe true
		}
	}

	fun matchedMatch(
		maleStatus: MatchMemberStatus = MatchMemberStatus.ACTIVE,
		femaleStatus: MatchMemberStatus = MatchMemberStatus.ACTIVE,
	): Match =
		MatchFixture.create(
			id = 7L,
			members = MatchFixture.membersOf(
				maleUserId = maleUserId,
				femaleUserId = femaleUserId,
				maleStatus = maleStatus,
				femaleStatus = femaleStatus,
			),
			status = MatchStatus.MATCHED,
		)

	describe("isLastActiveMember - 마지막 활성 참가자 판별") {
		it("상대가 아직 ACTIVE면 false다") {
			matchedMatch().isLastActiveMember(maleUserId) shouldBe false
		}

		it("상대가 이미 나가(DEACTIVE) 있으면 true다") {
			matchedMatch(femaleStatus = MatchMemberStatus.DEACTIVE).isLastActiveMember(maleUserId) shouldBe true
		}
	}

	describe("leave - 나가기") {

		it("혼자 나가면 본인만 DEACTIVE가 되고 상대·헤더는 그대로 유지된다") {
			val now: LocalDateTime = LocalDateTime.of(2026, 6, 17, 12, 0)

			val left: Match = matchedMatch().leave(maleUserId, now)

			left.status shouldBe MatchStatus.MATCHED
			left.deletedAt shouldBe null
			left.members.find(maleUserId)!!.status shouldBe MatchMemberStatus.DEACTIVE
			left.members.find(maleUserId)!!.deletedAt shouldBe null
			left.members.find(femaleUserId)!!.status shouldBe MatchMemberStatus.ACTIVE
		}

		it("상대가 이미 나간 뒤 마지막 한 명이 나가면 헤더를 CLOSED·소프트 삭제하고 전원 제거한다") {
			val now: LocalDateTime = LocalDateTime.of(2026, 6, 17, 12, 0)

			val closed: Match = matchedMatch(femaleStatus = MatchMemberStatus.DEACTIVE).leave(maleUserId, now)

			closed.status shouldBe MatchStatus.CLOSED
			closed.deletedAt shouldBe now
			closed.members.values.all { it.status == MatchMemberStatus.DEACTIVE } shouldBe true
			closed.members.values.all { it.deletedAt == now } shouldBe true
		}
	}

	describe("validateTerminable - 매칭 종료 가능 검증") {
		fun matchedMatch(): Match =
			MatchFixture.create(
				id = 7L,
				members = MatchFixture.membersOf(maleUserId = maleUserId, femaleUserId = femaleUserId),
				status = MatchStatus.MATCHED,
			)

		it("성사(MATCHED)된 매칭의 참가자가 종료하면 통과한다") {
			matchedMatch().validateTerminable(maleUserId)
		}

		it("참가자가 아니면 NOT_MATCH_PARTICIPANT를 던진다") {
			val ex: BusinessException = shouldThrow { matchedMatch().validateTerminable(99L) }
			ex.errorCode shouldBe MatchErrorCode.NOT_MATCH_PARTICIPANT
		}

		it("이미 종료(CLOSED)된 매칭이면 MATCH_ALREADY_CLOSED를 던진다") {
			val closed: Match = matchedMatch().delete(LocalDateTime.of(2026, 6, 17, 12, 0))

			val ex: BusinessException = shouldThrow { closed.validateTerminable(maleUserId) }
			ex.errorCode shouldBe MatchErrorCode.MATCH_ALREADY_CLOSED
		}

		it("아직 성사되지 않은(PROPOSED) 매칭이면 MATCH_NOT_MATCHED를 던진다") {
			val ex: BusinessException = shouldThrow { proposedMatch(id = 7L).validateTerminable(maleUserId) }
			ex.errorCode shouldBe MatchErrorCode.MATCH_NOT_MATCHED
		}

		it("이미 나간(DEACTIVE) 참가자가 다시 종료하려 하면 MATCH_ALREADY_CLOSED를 던진다") {
			val matchedWithLeftMale: Match = MatchFixture.create(
				id = 7L,
				members = MatchFixture.membersOf(
					maleUserId = maleUserId,
					femaleUserId = femaleUserId,
					maleStatus = MatchMemberStatus.DEACTIVE,
					femaleStatus = MatchMemberStatus.ACTIVE,
				),
				status = MatchStatus.MATCHED,
			)

			val ex: BusinessException = shouldThrow { matchedWithLeftMale.validateTerminable(maleUserId) }
			ex.errorCode shouldBe MatchErrorCode.MATCH_ALREADY_CLOSED
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

	describe("MatchEnded.from") {
		it("나간 사람의 상대를 수신자로 하는 이벤트를 만든다") {
			val match: Match = proposedMatch(id = 7L)

			val event: MatchEnded = MatchEnded.from(match, leftByUserId = maleUserId)

			event shouldBe MatchEnded(matchId = 7L, leftByUserId = maleUserId, partnerUserId = femaleUserId)
		}
	}
})
