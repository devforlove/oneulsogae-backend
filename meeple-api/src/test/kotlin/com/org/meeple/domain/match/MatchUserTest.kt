package com.org.meeple.domain.match

import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.core.common.event.MatchProfileSnapshot
import com.org.meeple.core.match.command.domain.MatchUser
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
		regionCode = 1,
		maritalStatus = MaritalStatus.SINGLE,
		nickname = "민수",
		profileImageCode = "1",
		lastLoginAt = LocalDateTime.of(2026, 6, 19, 9, 0),
	)

	describe("from") {
		it("스냅샷의 기준 필드를 그대로 매칭 읽기 모델로 옮긴다") {
			val matchUser: MatchUser = MatchUser.from(userId = 42L, snapshot = snapshot)

			matchUser shouldBe MatchUser(
				userId = 42L,
				gender = Gender.MALE,
				birthday = LocalDate.of(1995, 1, 1),
				regionCode = 1,
				maritalStatus = MaritalStatus.SINGLE,
				nickname = "민수",
				profileImageCode = "1",
				lastLoginAt = snapshot.lastLoginAt,
			)
		}
	}

	describe("partnerGender") {
		it("후보로 찾을 성별은 반대 성별이다") {
			MatchUser.from(1L, snapshot).partnerGender() shouldBe Gender.FEMALE
		}
	}
})
