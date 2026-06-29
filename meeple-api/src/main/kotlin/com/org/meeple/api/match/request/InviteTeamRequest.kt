package com.org.meeple.api.match.request

import com.org.meeple.core.teammatch.command.application.port.`in`.command.InviteTeamCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

/**
 * 팀 초대(결성) 요청. 인증 사용자가 [invitedUserId]를 초대해 [name]/[introduction]의 팀을 결성한다.
 * 이름은 필수(@NotBlank, 50자 이하), 소개는 필수(@NotBlank, 10자 이상 500자 이하), 초대 대상 userId는 필수(@NotNull)다.
 */
data class InviteTeamRequest(
	@field:NotNull(message = "초대할 사용자 ID는 필수입니다.")
	val invitedUserId: Long? = null,

	@field:NotBlank(message = "팀 이름은 필수입니다.")
	@field:Size(max = 50, message = "팀 이름은 50자 이하여야 합니다.")
	val name: String? = null,

	@field:NotBlank(message = "팀 소개는 필수입니다.")
	@field:Size(min = 10, max = 500, message = "팀 소개는 10자 이상 500자 이하여야 합니다.")
	val introduction: String? = null,

	@field:NotNull(message = "활동지역은 필수입니다.")
	@field:Positive(message = "활동지역 ID는 0보다 커야 합니다.")
	val regionId: Long? = null,
) {

	// @Valid 검증을 통과한 뒤 호출되므로, 필수 필드는 non-null이 보장된다. (command가 non-null 타입이라 여기서 풀어 넘긴다)
	fun toCommand(): InviteTeamCommand =
		InviteTeamCommand(
			invitedUserId = invitedUserId!!,
			name = name!!,
			introduction = introduction!!,
			regionId = regionId!!,
		)
}
