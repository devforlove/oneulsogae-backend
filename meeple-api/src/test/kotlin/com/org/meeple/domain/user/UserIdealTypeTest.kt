package com.org.meeple.domain.user

import com.org.meeple.common.user.DistancePreference
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.user.command.domain.UserIdealType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * [UserIdealType] 도메인 유닛 테스트.
 * 나이/키 범위 규칙(짝 존재·min ≤ max·경계)과 upsert 교체 시 식별자 보존을 검증한다.
 */
class UserIdealTypeTest : DescribeSpec({

	describe("of") {
		it("전 항목이 null이면 '상관없음' 이상형으로 생성된다") {
			val idealType: UserIdealType = UserIdealType.of(
				userId = 1L,
				ageMin = null, ageMax = null,
				heightMin = null, heightMax = null,
				maritalStatus = null, smokingStatus = null,
				drinkingStatus = null, religion = null, distance = null,
			)

			idealType.userId shouldBe 1L
			idealType.ageMin.shouldBeNull()
			idealType.distance.shouldBeNull()
		}

		it("정상 범위·enum이면 그대로 채워진다") {
			val idealType: UserIdealType = UserIdealType.of(
				userId = 1L,
				ageMin = 27, ageMax = 35,
				heightMin = 160, heightMax = 180,
				maritalStatus = MaritalStatus.SINGLE, smokingStatus = SmokingStatus.NON_SMOKER,
				drinkingStatus = null, religion = Religion.NONE, distance = DistancePreference.SAME_REGION,
			)

			idealType.ageMin shouldBe 27
			idealType.ageMax shouldBe 35
			idealType.distance shouldBe DistancePreference.SAME_REGION
		}

		it("나이 최소가 최대보다 크면 INVALID_IDEAL_TYPE_RANGE를 던진다") {
			val ex: BusinessException = shouldThrow {
				UserIdealType.of(
					userId = 1L,
					ageMin = 40, ageMax = 20,
					heightMin = null, heightMax = null,
					maritalStatus = null, smokingStatus = null,
					drinkingStatus = null, religion = null, distance = null,
				)
			}
			ex.errorCode shouldBe UserErrorCode.INVALID_IDEAL_TYPE_RANGE
		}

		it("나이 범위 한쪽만 주어지면 INVALID_IDEAL_TYPE_RANGE를 던진다") {
			shouldThrow<BusinessException> {
				UserIdealType.of(
					userId = 1L,
					ageMin = 30, ageMax = null,
					heightMin = null, heightMax = null,
					maritalStatus = null, smokingStatus = null,
					drinkingStatus = null, religion = null, distance = null,
				)
			}.errorCode shouldBe UserErrorCode.INVALID_IDEAL_TYPE_RANGE
		}

		it("나이가 허용 경계(20~60) 밖이면 INVALID_IDEAL_TYPE_RANGE를 던진다") {
			shouldThrow<BusinessException> {
				UserIdealType.of(
					userId = 1L,
					ageMin = 10, ageMax = 30,
					heightMin = null, heightMax = null,
					maritalStatus = null, smokingStatus = null,
					drinkingStatus = null, religion = null, distance = null,
				)
			}.errorCode shouldBe UserErrorCode.INVALID_IDEAL_TYPE_RANGE
		}

		it("키 최소가 최대보다 크면 INVALID_IDEAL_TYPE_RANGE를 던진다") {
			shouldThrow<BusinessException> {
				UserIdealType.of(
					userId = 1L,
					ageMin = null, ageMax = null,
					heightMin = 180, heightMax = 160,
					maritalStatus = null, smokingStatus = null,
					drinkingStatus = null, religion = null, distance = null,
				)
			}.errorCode shouldBe UserErrorCode.INVALID_IDEAL_TYPE_RANGE
		}
	}

	describe("update") {
		it("id와 userId를 보존하며 값을 교체한다") {
			val existing: UserIdealType = UserIdealType(
				id = 99L, userId = 1L,
				ageMin = 20, ageMax = 30,
			)

			val updated: UserIdealType = existing.update(
				ageMin = 27, ageMax = 35,
				heightMin = null, heightMax = null,
				maritalStatus = MaritalStatus.SINGLE, smokingStatus = null,
				drinkingStatus = null, religion = null, distance = DistancePreference.ADJACENT_REGION,
			)

			updated.id shouldBe 99L
			updated.userId shouldBe 1L
			updated.ageMin shouldBe 27
			updated.distance shouldBe DistancePreference.ADJACENT_REGION
		}

		it("교체 값이 잘못된 범위면 INVALID_IDEAL_TYPE_RANGE를 던진다") {
			val existing: UserIdealType = UserIdealType(id = 99L, userId = 1L)

			shouldThrow<BusinessException> {
				existing.update(
					ageMin = 50, ageMax = 40,
					heightMin = null, heightMax = null,
					maritalStatus = null, smokingStatus = null,
					drinkingStatus = null, religion = null, distance = null,
				)
			}.errorCode shouldBe UserErrorCode.INVALID_IDEAL_TYPE_RANGE
		}
	}
})
