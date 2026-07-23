package com.org.oneulsogae.admin.popup.query.service

import com.org.oneulsogae.admin.popup.command.domain.PopupImage
import com.org.oneulsogae.admin.popup.query.service.port.`in`.GetPopupImageUrlUseCase
import com.org.oneulsogae.admin.popup.query.service.port.out.PopupImageUrlPort
import org.springframework.stereotype.Service

/**
 * [GetPopupImageUrlUseCase] 구현. 팝업 프리픽스(popups/) key만 presigned GET URL을 발급한다.
 * (공개 프록시 /images/{key}가 팝업 이미지도 서빙할 수 있게 하는 화이트리스트 검증 포함)
 */
@Service
class GetPopupImageUrlService(
	private val popupImageUrlPort: PopupImageUrlPort,
) : GetPopupImageUrlUseCase {

	override fun execute(key: String): String? {
		if (!PopupImage.isValidKey(key)) return null
		return popupImageUrlPort.presignedGetUrl(key)
	}
}
