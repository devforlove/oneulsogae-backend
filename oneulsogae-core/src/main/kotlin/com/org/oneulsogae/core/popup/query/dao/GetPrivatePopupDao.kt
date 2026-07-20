package com.org.oneulsogae.core.popup.query.dao

import com.org.oneulsogae.core.popup.query.dto.PopupViews
import java.time.LocalDateTime

/**
 * 개인(private) 팝업 조회 dao(query out-port 인터페이스). (조회 전용 read model 반환)
 * 특정 사용자에게만 노출하는 개인 팝업(user_id = userId)만 다룬다. 실제 구현은 infra 레이어의 dao가 담당한다.
 */
interface GetPrivatePopupDao {

	/** [now] 기준 노출 기간 내인 [userId] 개인 팝업을 조회한다. (정렬은 read model [PopupViews]가 책임진다) */
	fun findVisible(now: LocalDateTime, userId: Long): PopupViews
}
