package com.org.oneulsogae.domain.match

import com.org.oneulsogae.common.match.MatchMemberStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.solomatch.command.domain.MatchMembers
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [MatchMembers] 도메인 유닛 테스트.
 * 참가자 식별·상대 식별·수락 집계와, 재소개 방지 멤버 키 산출을 검증한다. (N:N 확장의 토대)
 * 프레임워크·인프라 없이 순수 도메인 로직만 본다.
 */
class MatchMembersTest : DescribeSpec({

	fun members(): MatchMembers =
		MatchMembers.of(listOf(100L to Gender.MALE, 200L to Gender.FEMALE))

	describe("memberKey / memberKeyOf") {
		it("참가자 userId를 정렬해 이어 붙인다 (순서 무관 동일 키)") {
			members().memberKey() shouldBe "100-200"
			MatchMembers.memberKeyOf(listOf(200L, 100L)) shouldBe "100-200"
		}
	}

	describe("partnersOf") {
		it("나를 제외한 상대를 반환한다") {
			members().partnersOf(100L).map { it.userId } shouldBe listOf(200L)
		}
	}

	describe("apply / 신청 집계") {
		it("한 명만 신청하면 anyApplied=true, allApplied=false") {
			val responded: MatchMembers = members().apply(100L)

			responded.find(100L)!!.hasApplied shouldBe true
			responded.anyApplied() shouldBe true
			responded.allApplied() shouldBe false
		}

		it("전원 신청하면 allApplied=true") {
			val responded: MatchMembers = members().apply(100L).apply(200L)

			responded.allApplied() shouldBe true
		}

		it("신청 시 참가자 성별 기준 신청 비용을 paidInitAmount에 스냅샷한다 (남 32 / 여 16)") {
			val responded: MatchMembers = members().apply(100L).apply(200L)

			responded.find(100L)!!.paidInitAmount shouldBe 32
			responded.find(200L)!!.paidInitAmount shouldBe 16
		}
	}

	describe("withMatchId") {
		it("모든 참가자에 matchId를 채운 새 컬렉션을 돌려준다") {
			val assigned: MatchMembers = members().withMatchId(9L)

			assigned.values.all { it.matchId == 9L } shouldBe true
			// 원본은 그대로(불변)
			members().values.all { it.matchId == 0L } shouldBe true
		}
	}

	describe("activateAll") {
		it("전원을 ACTIVE로 승격한다") {
			val activated: MatchMembers = members().apply(100L).apply(200L).activateAll()

			activated.values.all { it.status == MatchMemberStatus.ACTIVE } shouldBe true
		}
	}
})
