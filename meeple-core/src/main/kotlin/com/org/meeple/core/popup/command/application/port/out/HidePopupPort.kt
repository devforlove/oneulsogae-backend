package com.org.meeple.core.popup.command.application.port.out

import java.time.LocalDateTime

/**
 * 팝업 숨김(soft-delete) 아웃포트. (명령 경로)
 * 주어진 팝업들을 [now] 시각으로 soft-delete해 이후 노출에서 제외한다. (행은 보존)
 */
interface HidePopupPort {

	fun hideByIds(ids: List<Long>, now: LocalDateTime)
}
