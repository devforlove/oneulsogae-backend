package com.org.meeple.domain.match

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.domain.MatchMembers
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

	describe("accept / 수락 집계") {
		it("한 명만 수락하면 anyAccepted=true, allAccepted=false") {
			val responded: MatchMembers = members().accept(100L)

			responded.find(100L)!!.isAccepted shouldBe true
			responded.anyAccepted() shouldBe true
			responded.allAccepted() shouldBe false
		}

		it("전원 수락하면 allAccepted=true") {
			val responded: MatchMembers = members().accept(100L).accept(200L)

			responded.allAccepted() shouldBe true
		}
	}
})
