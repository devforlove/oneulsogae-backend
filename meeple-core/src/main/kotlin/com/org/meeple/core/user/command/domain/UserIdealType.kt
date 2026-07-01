package com.org.meeple.core.user.command.domain

import com.org.meeple.common.user.DistancePreference
import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode

/**
 * 사용자 이상형(매칭 선호) 도메인 모델. [User]와 1:1이며, 매칭 후보에 바로 비교 가능한 이산 값으로 보관한다.
 * - 나이/키: 숫자 경계(min/max). 둘 다 null이면 "상관없음".
 * - 결혼/흡연/음주/종교/거리: enum. null이면 "상관없음"(해당 조건 미적용).
 * 매칭 반영은 이번 범위 밖이며, 저장/조회만 담당한다. 영속성은 [com.org.meeple.infra.user.command.entity.UserIdealTypeEntity]가 담당한다.
 */
data class UserIdealType(
	val id: Long = 0,
	val userId: Long,
	val ageMin: Int? = null,
	val ageMax: Int? = null,
	val heightMin: Int? = null,
	val heightMax: Int? = null,
	val maritalStatus: MaritalStatus? = null,
	val smokingStatus: SmokingStatus? = null,
	val drinkingStatus: DrinkingStatus? = null,
	val religion: Religion? = null,
	val distance: DistancePreference? = null,
) {

	init {
		validateIdealType()
	}

	/**
	 * 나이/키 범위 규칙을 검증한다. (생성·교체 모두 생성자 init에서 호출되므로 항상 유효 상태만 존재한다)
	 * - 범위는 최소·최대가 함께 존재하거나 함께 null이어야 한다. (한쪽만 입력 불가)
	 * - 최소 ≤ 최대이고, 허용 경계(나이 20~60, 키 150~195) 안이어야 한다.
	 */
	private fun validateIdealType() {
		validateRange(ageMin, ageMax, MIN_AGE, MAX_AGE)
		validateRange(heightMin, heightMax, MIN_HEIGHT, MAX_HEIGHT)
	}

	private fun validateRange(min: Int?, max: Int?, lower: Int, upper: Int) {
		if ((min == null) != (max == null)) {
			throw BusinessException(UserErrorCode.INVALID_IDEAL_TYPE_RANGE)
		}
		if (min != null && max != null) {
			if (min > max || min < lower || max > upper) {
				throw BusinessException(UserErrorCode.INVALID_IDEAL_TYPE_RANGE)
			}
		}
	}

	/** upsert 갱신 경로. id/userId를 보존하고 선호 값을 교체한다. (copy가 init 검증을 다시 태운다) */
	fun update(
		ageMin: Int?,
		ageMax: Int?,
		heightMin: Int?,
		heightMax: Int?,
		maritalStatus: MaritalStatus?,
		smokingStatus: SmokingStatus?,
		drinkingStatus: DrinkingStatus?,
		religion: Religion?,
		distance: DistancePreference?,
	): UserIdealType =
		copy(
			ageMin = ageMin,
			ageMax = ageMax,
			heightMin = heightMin,
			heightMax = heightMax,
			maritalStatus = maritalStatus,
			smokingStatus = smokingStatus,
			drinkingStatus = drinkingStatus,
			religion = religion,
			distance = distance,
		)

	companion object {

		private const val MIN_AGE: Int = 20
		private const val MAX_AGE: Int = 60
		private const val MIN_HEIGHT: Int = 150
		private const val MAX_HEIGHT: Int = 195

		/** 신규 이상형을 생성한다. (init 검증 통과분만 반환) */
		fun of(
			userId: Long,
			ageMin: Int?,
			ageMax: Int?,
			heightMin: Int?,
			heightMax: Int?,
			maritalStatus: MaritalStatus?,
			smokingStatus: SmokingStatus?,
			drinkingStatus: DrinkingStatus?,
			religion: Religion?,
			distance: DistancePreference?,
		): UserIdealType =
			UserIdealType(
				userId = userId,
				ageMin = ageMin,
				ageMax = ageMax,
				heightMin = heightMin,
				heightMax = heightMax,
				maritalStatus = maritalStatus,
				smokingStatus = smokingStatus,
				drinkingStatus = drinkingStatus,
				religion = religion,
				distance = distance,
			)
	}
}
