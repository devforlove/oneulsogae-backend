package com.org.meeple.api.user.response

import com.org.meeple.common.user.DistancePreference
import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus
import com.org.meeple.core.user.command.domain.UserIdealType
import com.org.meeple.core.user.query.dto.IdealTypeView

/**
 * 이상형 응답. 프론트 `IdealType`와 필드·형태를 맞춘다(배열형 ageRange/heightRange, enum name).
 * null = "상관없음". 나이/키는 min·max가 모두 있을 때만 배열로, 하나라도 없으면 null로 내려간다.
 */
data class IdealTypeResponse(
	val ageRange: List<Int>?,
	val heightRange: List<Int>?,
	val maritalStatus: MaritalStatus?,
	val smoking: SmokingStatus?,
	val drinking: DrinkingStatus?,
	val religion: Religion?,
	val distance: DistancePreference?,
) {
	companion object {

		/** 조회(query) read model 매핑. 미설정(null)이면 전 항목 null인 [empty] 응답을 준다. */
		fun of(view: IdealTypeView?): IdealTypeResponse =
			if (view == null) empty()
			else IdealTypeResponse(
				ageRange = range(view.ageMin, view.ageMax),
				heightRange = range(view.heightMin, view.heightMax),
				maritalStatus = view.maritalStatus,
				smoking = view.smokingStatus,
				drinking = view.drinkingStatus,
				religion = view.religion,
				distance = view.distance,
			)

		/** 명령(command) 결과 도메인 매핑. (저장 후 갱신된 [UserIdealType] 렌더링) */
		fun of(domain: UserIdealType): IdealTypeResponse =
			IdealTypeResponse(
				ageRange = range(domain.ageMin, domain.ageMax),
				heightRange = range(domain.heightMin, domain.heightMax),
				maritalStatus = domain.maritalStatus,
				smoking = domain.smokingStatus,
				drinking = domain.drinkingStatus,
				religion = domain.religion,
				distance = domain.distance,
			)

		/** 미설정 사용자 응답. 전 항목 null("상관없음"). */
		fun empty(): IdealTypeResponse =
			IdealTypeResponse(null, null, null, null, null, null, null)

		private fun range(min: Int?, max: Int?): List<Int>? =
			if (min != null && max != null) listOf(min, max) else null
	}
}
