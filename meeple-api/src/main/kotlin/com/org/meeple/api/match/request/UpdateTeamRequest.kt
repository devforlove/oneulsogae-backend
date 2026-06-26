package com.org.meeple.api.match.request

import com.org.meeple.core.match.command.application.port.`in`.command.UpdateTeamCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * 팀 정보 수정 요청. 팀의 표시 정보인 이름·소개·활동지역을 전체 교체한다.
 * 이름은 필수(@NotBlank, 50자 이하), 소개는 필수(@NotBlank, 10자 이상 100자 미만), 활동지역 id는 필수(@NotNull)다.
 */
data class UpdateTeamRequest(
	@field:NotBlank(message = "팀 이름은 필수입니다.")
	@field:Size(max = 50, message = "팀 이름은 50자 이하여야 합니다.")
	val name: String? = null,

	@field:NotBlank(message = "팀 소개는 필수입니다.")
	@field:Size(min = 10, max = 99, message = "팀 소개는 10자 이상 100자 미만이어야 합니다.")
	val introduction: String? = null,

	@field:NotNull(message = "활동지역은 필수입니다.")
	val regionId: Long? = null,
) {

	// @Valid 검증을 통과한 뒤 호출되므로, 필수 필드는 non-null이 보장된다. (command가 non-null 타입이라 여기서 풀어 넘긴다)
	fun toCommand(): UpdateTeamCommand =
		UpdateTeamCommand(
			name = name!!,
			introduction = introduction!!,
			regionId = regionId!!,
		)
}
