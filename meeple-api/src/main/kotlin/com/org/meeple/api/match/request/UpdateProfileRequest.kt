package com.org.meeple.api.match.request

import com.org.meeple.common.user.BodyType
import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus
import com.org.meeple.core.user.command.service.port.`in`.command.UpdateProfileCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * 프로필 수정 요청. 전체 교체(PUT) 의미이므로 모든 편집 필드를 채워 보내야 한다.
 * 나이/성별/키/휴대폰번호/회사이메일은 프로필 수정으로 바꿀 수 없어 받지 않는다.
 * 편집 필드는 모두 필수다. (문자열은 @NotBlank, 목록은 @NotEmpty, enum은 @NotNull로 null·빈 값을 막는다)
 */
data class UpdateProfileRequest(
	@field:NotBlank(message = "닉네임은 필수입니다.")
	@field:Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다.")
	val nickname: String? = null,

	@field:NotBlank(message = "프로필 이미지 코드는 필수입니다.")
	@field:Size(max = 50, message = "프로필 이미지 코드는 50자 이하여야 합니다.")
	val profileImageCode: String? = null,

	@field:NotBlank(message = "직업은 필수입니다.")
	@field:Size(max = 20, message = "직업은 20자 이하여야 합니다.")
	val job: String? = null,

	@field:NotBlank(message = "활동 지역은 필수입니다.")
	@field:Size(max = 100, message = "활동 지역은 100자 이하여야 합니다.")
	val activityArea: String? = null,

	@field:NotBlank(message = "자기소개는 필수입니다.")
	@field:Size(max = 1000, message = "자기소개는 1000자 이하여야 합니다.")
	val introduction: String? = null,

	@field:NotEmpty(message = "특성은 최소 1개 이상 선택해야 합니다.")
	@field:Size(max = 10, message = "특성은 최대 10개까지 선택할 수 있습니다.")
	val traits: List<@Size(max = 20, message = "특성은 항목당 20자 이하여야 합니다.") String> = emptyList(),

	@field:NotEmpty(message = "관심사는 최소 1개 이상 선택해야 합니다.")
	@field:Size(max = 10, message = "관심사는 최대 10개까지 선택할 수 있습니다.")
	val interests: List<@Size(max = 20, message = "관심사는 항목당 20자 이하여야 합니다.") String> = emptyList(),

	@field:NotNull(message = "결혼 여부는 필수입니다.")
	val maritalStatus: MaritalStatus? = null,

	@field:NotNull(message = "흡연 여부는 필수입니다.")
	val smokingStatus: SmokingStatus? = null,

	@field:NotNull(message = "종교는 필수입니다.")
	val religion: Religion? = null,

	@field:NotNull(message = "음주 여부는 필수입니다.")
	val drinkingStatus: DrinkingStatus? = null,

	@field:NotNull(message = "체형은 필수입니다.")
	val bodyType: BodyType? = null,
) {

	// @Valid 검증을 통과한 뒤 호출되므로, 필수 필드는 non-null이 보장된다. (command가 non-null 타입이라 여기서 풀어 넘긴다)
	fun toCommand(): UpdateProfileCommand =
		UpdateProfileCommand(
			nickname = nickname!!,
			profileImageCode = profileImageCode!!,
			job = job!!,
			activityArea = activityArea!!,
			introduction = introduction!!,
			traits = traits,
			interests = interests,
			maritalStatus = maritalStatus!!,
			smokingStatus = smokingStatus!!,
			religion = religion!!,
			drinkingStatus = drinkingStatus!!,
			bodyType = bodyType!!,
		)
}
