package com.org.meeple.core.popup.application.port.out

import com.org.meeple.core.popup.domain.Popup

/**
 * 팝업 저장 아웃포트.
 * 신규 팝업을 저장하거나, 기존 팝업(id 존재)의 변경분을 반영한다.
 */
interface SavePopupPort {

	fun save(popup: Popup): Popup
}
