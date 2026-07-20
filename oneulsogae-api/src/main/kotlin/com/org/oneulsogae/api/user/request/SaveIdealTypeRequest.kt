package com.org.oneulsogae.api.user.request

import com.org.oneulsogae.common.user.DrinkingStatus
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.Religion
import com.org.oneulsogae.common.user.SmokingStatus
import com.org.oneulsogae.core.user.command.application.port.`in`.command.SaveIdealTypeCommand
import jakarta.validation.constraints.Size

/**
 * 이상형 저장 요청(PUT, 전체 교체). 모든 항목은 선택이며 생략(null)은 "상관없음"을 뜻한다.
 * enum은 백엔드 enum name으로 받는다. 나이/키 범위는 `[최소, 최대]` 2요소 배열로 받아 명령에서 min/max로 분해한다.
 * 값 규칙(min ≤ max·경계·짝 존재)은 도메인 [com.org.oneulsogae.core.user.command.domain.UserIdealType]가 검증한다.
 */
data class SaveIdealTypeRequest(
	@field:Size(min = 2, max = 2, message = "나이 범위는 [최소, 최대] 두 값이어야 합니다.")
	val ageRange: List<Int>? = null,

	@field:Size(min = 2, max = 2, message = "키 범위는 [최소, 최대] 두 값이어야 합니다.")
	val heightRange: List<Int>? = null,

	val maritalStatus: MaritalStatus? = null,

	val smoking: SmokingStatus? = null,

	val drinking: DrinkingStatus? = null,

	val religion: Religion? = null,
) {

	fun toCommand(): SaveIdealTypeCommand =
		SaveIdealTypeCommand(
			ageMin = ageRange?.get(0),
			ageMax = ageRange?.get(1),
			heightMin = heightRange?.get(0),
			heightMax = heightRange?.get(1),
			maritalStatus = maritalStatus,
			smokingStatus = smoking,
			drinkingStatus = drinking,
			religion = religion,
		)
}
