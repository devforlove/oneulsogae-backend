package com.org.oneulsogae.admin.popup.command.application

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.popup.command.application.port.out.SavePopupImageTemplatePort
import com.org.oneulsogae.admin.popup.command.application.port.out.UploadPopupImagePort
import com.org.oneulsogae.admin.popup.command.domain.PopupImage
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.UUID
import javax.imageio.ImageIO

/**
 * 팝업 이미지 등록기. 생성·수정 서비스가 공유한다.
 * 파일을 검증([PopupImage.validate])하고 치수를 읽어 스토리지에 올린 뒤,
 * 이미지 템플릿(image_templates)을 만들거나(신규) 기존 코드의 이미지를 교체해(수정) 팝업이 참조할 코드를 돌려준다.
 * (템플릿 코드를 재사용하므로 이미지를 갈아끼워도 팝업의 image_code는 바뀌지 않는다)
 */
@Component
class PopupImageRegistrar(
	private val uploadPopupImagePort: UploadPopupImagePort,
	private val savePopupImageTemplatePort: SavePopupImageTemplatePort,
) {

	/**
	 * 이미지를 업로드하고 템플릿에 반영한 뒤 팝업이 참조할 코드를 반환한다.
	 * [existingCode]가 있으면 그 템플릿의 이미지를 교체하고, 없으면 새 코드를 만든다.
	 */
	fun register(existingCode: String?, content: ByteArray, contentType: String?, size: Long): String {
		PopupImage.validate(contentType, size)
		val image: BufferedImage = ImageIO.read(ByteArrayInputStream(content))
			?: throw AdminException(AdminErrorCode.POPUP_INVALID_IMAGE_TYPE, "이미지 파일을 해석할 수 없습니다.")

		val resolvedContentType: String = contentType!!
		val key: String = "${PopupImage.KEY_PREFIX}/${UUID.randomUUID()}.${PopupImage.extensionOf(resolvedContentType)}"
		uploadPopupImagePort.upload(key, content, resolvedContentType)

		val code: String = existingCode ?: "POPUP_ADMIN_${UUID.randomUUID()}"
		savePopupImageTemplatePort.upsert(code, key, image.width, image.height)
		return code
	}
}
