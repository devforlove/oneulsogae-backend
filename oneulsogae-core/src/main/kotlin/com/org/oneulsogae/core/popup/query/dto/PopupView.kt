package com.org.oneulsogae.core.popup.query.dto

import com.org.oneulsogae.common.popup.PopupType

/**
 * 노출 중인 팝업 한 건의 조회 전용 read model.
 * [imageUrl]/[linkUrl]/[buttonText]는 없으면 null이며, [popUpType]은 팝업 유형(일반/출석 보상 등)이다.
 * 명령 도메인([com.org.oneulsogae.core.popup.command.domain.Popup])과 분리해, 조회 경로는 이 투영만 다룬다.
 */
data class PopupView(
	val id: Long,
	val title: String?,
	val description: String?,
	val displayOrder: Int,
	val imageUrl: String?,
	val imageWidth: Int?,
	val imageHeight: Int?,
	val linkUrl: String?,
	val buttonText: String?,
	val popUpType: PopupType,
)
