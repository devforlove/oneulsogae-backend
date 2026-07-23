package com.org.oneulsogae.admin.popup.command.application

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.popup.command.application.port.`in`.UpdateAdminPopupUseCase
import com.org.oneulsogae.admin.popup.command.application.port.`in`.command.AdminPopupCommand
import com.org.oneulsogae.admin.popup.command.application.port.out.LoadAdminPopupPort
import com.org.oneulsogae.admin.popup.command.application.port.out.SaveAdminPopupPort
import com.org.oneulsogae.admin.popup.command.domain.AdminPopup
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [UpdateAdminPopupUseCase] 구현. (명령)
 * 대상 전역 팝업을 로드해 없으면(개인 팝업·삭제 포함) POPUP_NOT_FOUND.
 * 새 이미지가 오면 업로드해 기존 템플릿 코드의 이미지를 교체하고, 없으면 기존 이미지를 유지한다.
 * 전체 데이터를 [AdminPopupCommand]로 교체(검증 포함)한 뒤 저장한다. (id·생성 시각은 보존)
 */
@Service
@Transactional
class UpdateAdminPopupService(
	private val loadAdminPopupPort: LoadAdminPopupPort,
	private val saveAdminPopupPort: SaveAdminPopupPort,
	private val popupImageRegistrar: PopupImageRegistrar,
) : UpdateAdminPopupUseCase {

	override fun update(id: Long, command: AdminPopupCommand) {
		val existing: AdminPopup = loadAdminPopupPort.loadById(id)
			?: throw AdminException(AdminErrorCode.POPUP_NOT_FOUND, "팝업을 찾을 수 없습니다: $id")
		val imageCode: String? = command.imageContent?.let { content: ByteArray ->
			popupImageRegistrar.register(existing.imageCode, content, command.imageContentType, command.imageSize)
		} ?: existing.imageCode
		saveAdminPopupPort.save(command.toPopup(id = id, imageCode = imageCode))
	}
}
