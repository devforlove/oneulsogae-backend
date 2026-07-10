package com.org.meeple.api.user.request

import com.org.meeple.common.user.BodyType
import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus
import com.org.meeple.core.user.command.application.port.`in`.command.UpdateUserDetailCommand
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate

/**
 * 온보딩 완료 요청 본문. 프로필 상세를 받아 저장하고(전체 교체) 정식 가입(ACTIVE) 처리한다.
 * id/userId/profileImageCode는 서버가 관리하므로 받지 않는다. (profileImageCode는 서버가 랜덤 배정한다)
 * 회사 이메일/회사명은 온보딩과 분리된 회사 인증 플로우에서만 다루므로 여기서 받지 않는다.
 * nullable 필드는 값이 있을 때만 제약을 검사한다. (null은 통과)
 */
data class UpdateUserDetailRequest(
	@field:NotBlank(message = "닉네임은 필수입니다.")
	@field:Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다.")
	val nickname: String? = null,

	val birthday: LocalDate? = null,

	@field:NotNull(message = "키는 필수입니다.")
	@field:Min(value = 140, message = "키는 140cm 이상이어야 합니다.")
	@field:Max(value = 250, message = "키는 250cm 이하여야 합니다.")
	val height: Int? = null,

	val gender: Gender? = null,

	val phoneNumber: String? = null,

	@field:NotBlank(message = "직업은 필수입니다.")
	@field:Size(max = 20, message = "직업은 20자 이하여야 합니다.")
	val job: String? = null,

	@field:NotNull(message = "활동 지역은 필수입니다.")
	val regionId: Long? = null,

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
	fun toCommand(): UpdateUserDetailCommand =
		UpdateUserDetailCommand(
			nickname = nickname!!,
			birthday = birthday,
			height = height!!,
			gender = gender,
			phoneNumber = phoneNumber,
			job = job!!,
			regionId = regionId!!,
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
