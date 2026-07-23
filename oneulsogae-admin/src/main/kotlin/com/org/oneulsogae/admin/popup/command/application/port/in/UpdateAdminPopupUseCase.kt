package com.org.oneulsogae.admin.popup.command.application.port.`in`

import com.org.oneulsogae.admin.popup.command.application.port.`in`.command.AdminPopupCommand

/** 어드민 전역 팝업 수정(전체 교체) 유스케이스. 없으면 POPUP_NOT_FOUND. */
fun interface UpdateAdminPopupUseCase {

	fun update(id: Long, command: AdminPopupCommand)
}
