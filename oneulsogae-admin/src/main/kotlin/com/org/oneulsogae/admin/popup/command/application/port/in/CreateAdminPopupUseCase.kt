package com.org.oneulsogae.admin.popup.command.application.port.`in`

import com.org.oneulsogae.admin.popup.command.application.port.`in`.command.AdminPopupCommand

/** 어드민 전역 팝업 생성 유스케이스. */
fun interface CreateAdminPopupUseCase {

	fun create(command: AdminPopupCommand)
}
