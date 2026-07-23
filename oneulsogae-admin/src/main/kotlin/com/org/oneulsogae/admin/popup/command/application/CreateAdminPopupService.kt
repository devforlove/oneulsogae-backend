package com.org.oneulsogae.admin.popup.command.application

import com.org.oneulsogae.admin.popup.command.application.port.`in`.CreateAdminPopupUseCase
import com.org.oneulsogae.admin.popup.command.application.port.`in`.command.AdminPopupCommand
import com.org.oneulsogae.admin.popup.command.application.port.out.SaveAdminPopupPort
import com.org.oneulsogae.admin.popup.command.domain.AdminPopup
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [CreateAdminPopupUseCase] 구현. [AdminPopupCommand]로 전역 팝업([AdminPopup])을 만들어 저장한다.
 * 기간 순서·생성 가능 유형 검증은 도메인([AdminPopup.create])이 한다.
 */
@Service
@Transactional
class CreateAdminPopupService(
	private val saveAdminPopupPort: SaveAdminPopupPort,
) : CreateAdminPopupUseCase {

	override fun create(command: AdminPopupCommand) {
		saveAdminPopupPort.save(command.toPopup())
	}
}

/** 명령을 도메인 모델로 변환한다. (생성과 수정이 같은 변환을 공유 — id만 다르다) */
internal fun AdminPopupCommand.toPopup(id: Long = 0): AdminPopup =
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
