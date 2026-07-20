package com.org.oneulsogae.core.popup.command.application.port.`in`

/**
 * 팝업 숨김(soft-delete) 유스케이스(in-port). (명령 경로)
 * 한 번 조회하면 제거할 팝업([com.org.oneulsogae.common.popup.PopupType.removeAfterView])을 조회 시점에 숨길 때 쓴다.
 * "어떤 팝업이 1회성인지" 판단은 호출 측(조회)이 책임지고, 이 유스케이스는 받은 id들을 soft-delete만 한다.
 */
interface HidePopupUseCase {

	/** 주어진 팝업들을 soft-delete한다. (빈 목록이면 아무 일도 하지 않는다) */
	fun hideByIds(ids: List<Long>)
}
