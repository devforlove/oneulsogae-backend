package com.org.meeple.core.popup.query.dao

import com.org.meeple.core.popup.query.dto.PopupViews
import java.time.LocalDateTime

/**
 * 전역(public) 팝업 조회 dao(query out-port 인터페이스). (조회 전용 read model 반환)
 * 모든 사용자에게 노출하는 전역 팝업(user_id is null)만 다룬다. 실제 구현은 infra 레이어의 dao가 담당한다.
 */
interface GetPublicPopupDao {

	/** [now] 기준 노출 기간 내인 전역 팝업을 조회한다. (정렬은 read model [PopupViews]가 책임진다) */
	fun findVisible(now: LocalDateTime): PopupViews
}
