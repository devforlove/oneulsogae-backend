package com.org.oneulsogae.api.report.request

import com.org.oneulsogae.common.report.ReportType
import com.org.oneulsogae.core.report.command.application.port.`in`.command.CreateReportCommand
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class CreateReportRequest(
	@field:NotNull(message = "신고 사유는 필수입니다.")
	val type: ReportType? = null,

	@field:NotNull(message = "채팅방 id는 필수입니다.")
	val chatRoomId: Long? = null,

	@field:Size(max = 1000, message = "신고 내용은 1000자 이하여야 합니다.")
	val description: String? = null,
) {
	// @Valid 통과 후 호출 → 필수 필드(type·chatRoomId) non-null 보장 → command로 변환
	fun toCommand(): CreateReportCommand =
		CreateReportCommand(
			type = type!!,
			chatRoomId = chatRoomId!!,
			description = description,
		)
}
