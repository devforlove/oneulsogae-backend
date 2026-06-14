package com.org.meeple.domain.match

import com.org.meeple.common.user.Gender
import com.org.meeple.core.fixture.MatchFixture
import com.org.meeple.core.match.domain.Match
import com.org.meeple.core.match.domain.MatchMembers
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [MatchMembers] 도메인 유닛 테스트.
 * 1:1 매칭의 male/female를 성별을 가진 참가자 행으로 펼치는 파생을 검증한다. (N:N 확장의 토대)
 * 프레임워크·인프라 없이 순수 도메인 로직만 본다.
 */
class MatchMembersTest : DescribeSpec({

	describe("from") {
		it("1:1 매칭의 male/female를 각각 참가자(성별 포함)로 펼친다") {
			val match: Match = MatchFixture.create(id = 7L, maleUserId = 100L, femaleUserId = 200L)

			val members: MatchMembers = MatchMembers.from(match)

			members.size shouldBe 2
			members.values.map { Triple(it.matchId, it.userId, it.gender) } shouldBe listOf(
				Triple(7L, 100L, Gender.MALE),
				Triple(7L, 200L, Gender.FEMALE),
			)
		}
	}
})
