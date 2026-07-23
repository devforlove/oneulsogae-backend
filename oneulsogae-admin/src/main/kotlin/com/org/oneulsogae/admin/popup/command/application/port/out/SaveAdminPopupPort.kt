package com.org.oneulsogae.admin.popup.command.application.port.out

import com.org.oneulsogae.admin.popup.command.domain.AdminPopup

/** 어드민 전역 팝업 저장 out-port. id가 0이면 INSERT, 0이 아니면 기존 행 교체(UPDATE). infra 어댑터가 구현한다. */
fun interface SaveAdminPopupPort {

	fun save(popup: AdminPopup)
}
