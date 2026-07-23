package com.org.oneulsogae.admin.popup.command.application.port.`in`.command

import com.org.oneulsogae.common.popup.PopupType
import java.time.LocalDateTime

/**
 * 어드민 팝업 생성/수정 입력. 생성·수정이 같은 필드 전체를 받으므로 하나의 명령을 공유한다.
 * 이미지는 코드가 아니라 파일로 받는다([imageContent]) — 서버가 업로드하고 이미지 템플릿을 저장해 코드를 채운다.
 */
data class AdminPopupCommand(
	val title: String?,
	val description: String?,
	val displayOrder: Int,
	val linkUrl: String?,
	val buttonText: String?,
	val popUpType: PopupType,
	/** 노출 시작 시각. null이면 제한 없음. */
	val exposedFrom: LocalDateTime?,
	/** 노출 종료 시각. null이면 제한 없음. */
	val exposedTo: LocalDateTime?,
	/** 팝업 이미지 파일. null이면 생성 시 이미지 없음, 수정 시 기존 이미지 유지. */
	val imageContent: ByteArray? = null,
	val imageContentType: String? = null,
	val imageSize: Long = 0,
)
