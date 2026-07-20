package com.org.oneulsogae.domain.match

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.core.common.event.MatchProfileSnapshot
import com.org.oneulsogae.core.matchuser.command.domain.MatchUser
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [MatchUser] 도메인 유닛 테스트.
 * user 도메인 스냅샷으로부터의 변환과, 후보 성별(반대 성별) 산출을 검증한다.
 */
class MatchUserTest : DescribeSpec({

	val snapshot = MatchProfileSnapshot(
		gender = Gender.MALE,
		birthday = LocalDate.of(1995, 1, 1),
		regionId = 7L,
		maritalStatus = MaritalStatus.SINGLE,
		nickname = "민수",
		profileImageCode = "1",
		lastLoginAt = LocalDateTime.of(2026, 6, 19, 9, 0),
		companyName = "오늘의 소개컴퍼니",
	)

	describe("from") {
		it("스냅샷의 기준 필드를 그대로 매칭 읽기 모델로 옮긴다 (같은 회사 소개 거부는 신규 적재 기본값 true)") {
			val matchUser: MatchUser = MatchUser.from(userId = 42L, snapshot = snapshot)

			matchUser shouldBe MatchUser(
				userId = 42L,
				gender = Gender.MALE,
				birthday = LocalDate.of(1995, 1, 1),
				regionId = 7L,
				maritalStatus = MaritalStatus.SINGLE,
				nickname = "민수",
				profileImageCode = "1",
				lastLoginAt = snapshot.lastLoginAt,
				companyName = "오늘의 소개컴퍼니",
				refuseSameCompanyIntro = true,
			)
		}
	}

	describe("partnerGender") {
		it("후보로 찾을 성별은 반대 성별이다") {
			MatchUser.from(1L, snapshot).partnerGender() shouldBe Gender.FEMALE
		}
	}
})
