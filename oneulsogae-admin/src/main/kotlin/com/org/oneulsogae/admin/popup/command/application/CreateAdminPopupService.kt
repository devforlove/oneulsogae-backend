package com.org.oneulsogae.admin.popup.command.application

import com.org.oneulsogae.admin.popup.command.application.port.`in`.CreateAdminPopupUseCase
import com.org.oneulsogae.admin.popup.command.application.port.`in`.command.AdminPopupCommand
import com.org.oneulsogae.admin.popup.command.application.port.out.SaveAdminPopupPort
import com.org.oneulsogae.admin.popup.command.domain.AdminPopup
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [CreateAdminPopupUseCase] 구현. [AdminPopupCommand]로 전역 팝업([AdminPopup])을 만들어 저장한다.
 * 이미지 파일이 오면 업로드·템플릿 저장([PopupImageRegistrar])을 먼저 하고 그 코드를 팝업에 연결한다.
 * 기간 순서·생성 가능 유형 검증은 도메인([AdminPopup.create])이 한다.
 */
@Service
@Transactional
class CreateAdminPopupService(
	private val saveAdminPopupPort: SaveAdminPopupPort,
	private val popupImageRegistrar: PopupImageRegistrar,
) : CreateAdminPopupUseCase {

	override fun create(command: AdminPopupCommand) {
		val imageCode: String? = command.imageContent?.let { content: ByteArray ->
			popupImageRegistrar.register(null, content, command.imageContentType, command.imageSize)
		}
		saveAdminPopupPort.save(command.toPopup(imageCode = imageCode))
	}
}

/** 명령을 도메인 모델로 변환한다. (생성과 수정이 같은 변환을 공유 — id·이미지 코드만 다르다) */
internal fun AdminPopupCommand.toPopup(id: Long = 0, imageCode: String?): AdminPopup =
	AdminPopup.create(
		id = id,
		title = title,
		description = description,
		displayOrder = displayOrder,
		imageCode = imageCode,
		linkUrl = linkUrl,
		buttonText = buttonText,
		popUpType = popUpType,
		exposedFrom = exposedFrom,
		exposedTo = exposedTo,
	)
