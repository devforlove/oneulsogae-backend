package com.org.meeple.api.match.response

import com.org.meeple.common.user.BodyType
import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus
import com.org.meeple.core.region.query.dto.RegionView

/** 온보딩에서 사용자가 선택할 수 있는 enum 타입별 옵션 목록 + 활동지역 전체 목록 응답. */
data class ProfileOptionsResponse(
	/** 체형은 성별별로 구분해서 내려준다. */
	val bodyTypes: Map<Gender, List<EnumOption>>,
	val drinkingStatuses: List<EnumOption>,
	val maritalStatuses: List<EnumOption>,
	val religions: List<EnumOption>,
	val smokingStatuses: List<EnumOption>,
	/** 활동지역(시/도 + 시/군/구) 전체 목록. */
	val regions: List<RegionOption>,
) {

	/** enum 옵션. [name]은 enum 상수명, [description]은 화면에 노출할 한글 설명. */
	data class EnumOption(
		val name: String,
		val description: String,
	)

	/** 활동지역 옵션. */
	data class RegionOption(
		val id: Long,
		val sido: String,
		val sigungu: String,
	)

	companion object {
		fun of(regions: List<RegionView>): ProfileOptionsResponse =
			ProfileOptionsResponse(
				bodyTypes = BodyType.entries.groupBy({ it.gender }, { EnumOption(it.name, it.description) }),
				drinkingStatuses = DrinkingStatus.entries.map { EnumOption(it.name, it.description) },
				maritalStatuses = MaritalStatus.entries.map { EnumOption(it.name, it.description) },
				religions = Religion.entries.map { EnumOption(it.name, it.description) },
				smokingStatuses = SmokingStatus.entries.map { EnumOption(it.name, it.description) },
				regions = regions.map { region: RegionView -> RegionOption(region.id, region.sido, region.sigungu) },
			)
	}
}
