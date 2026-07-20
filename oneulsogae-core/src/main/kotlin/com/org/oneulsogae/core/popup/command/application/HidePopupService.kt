package com.org.oneulsogae.core.popup.command.application

import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.popup.command.application.port.`in`.HidePopupUseCase
import com.org.oneulsogae.core.popup.command.application.port.out.HidePopupPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [HidePopupUseCase] 구현. (명령 경로 — 자체 트랜잭션)
 * 주어진 팝업들을 현재 시각으로 soft-delete한다. 빈 목록이면 아무 일도 하지 않는다.
 * 조회 흐름(GetPopupsService)이 1회성 팝업을 본 시점에 이 in-port로 위임해 다음 조회부터 노출을 막는다.
 */
@Service
@Transactional
class HidePopupService(
	private val hidePopupPort: HidePopupPort,
	private val timeGenerator: TimeGenerator,
) : HidePopupUseCase {

	override fun hideByIds(ids: List<Long>) {
		if (ids.isEmpty()) return
		hidePopupPort.hideByIds(ids, timeGenerator.now())
	}
}
