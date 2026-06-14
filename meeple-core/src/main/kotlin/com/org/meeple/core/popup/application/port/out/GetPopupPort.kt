package com.org.meeple.core.popup.application.port.out

import com.org.meeple.core.popup.domain.Popup
import com.org.meeple.core.popup.domain.Popups
import java.time.LocalDateTime

/**
 * 팝업 조회 아웃포트.
 * 도메인 모델([Popup]/[Popups])만을 반환하며, 실제 구현은 infra 레이어의 어댑터가 담당한다.
 */
interface GetPopupPort {

	/** id로 팝업을 조회한다. 없으면 null. */
	fun findById(id: Long): Popup?

	/** [now] 기준 노출 대상(노출 ON + 기간 내)인 팝업을 display_order 오름차순으로 조회한다. */
	fun findVisible(now: LocalDateTime): Popups
}
