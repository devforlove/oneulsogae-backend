package com.org.oneulsogae.admin.popup.command.application.port.`in`.command

import com.org.oneulsogae.common.popup.PopupType
import java.time.LocalDateTime

/** 어드민 팝업 생성/수정 입력. 생성·수정이 같은 필드 전체를 받으므로 하나의 명령을 공유한다. */
data class AdminPopupCommand(
	val title: String?,
	val description: String?,
	val displayOrder: Int,
	val imageCode: String?,
	val linkUrl: String?,
	val buttonText: String?,
	val popUpType: PopupType,
	/** 노출 시작 시각. null이면 제한 없음. */
	val exposedFrom: LocalDateTime?,
	/** 노출 종료 시각. null이면 제한 없음. */
	val exposedTo: LocalDateTime?,
)
