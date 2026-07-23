package com.org.oneulsogae.admin.popup.command.application.port.out

import com.org.oneulsogae.admin.popup.command.domain.AdminPopup

/** 어드민 전역 팝업 단건 로드 out-port. 개인 팝업(user_id 보유)·삭제된 팝업은 없는 것으로 본다. infra 어댑터가 구현한다. */
fun interface LoadAdminPopupPort {

	fun loadById(id: Long): AdminPopup?
}
