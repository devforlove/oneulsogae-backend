package com.org.oneulsogae.infra.image.adapter

import com.org.oneulsogae.admin.popup.command.application.port.out.SavePopupImageTemplatePort
import com.org.oneulsogae.infra.config.ImageProxyProperties
import com.org.oneulsogae.infra.image.entity.ImageTemplateEntity
import com.org.oneulsogae.infra.image.repository.ImageTemplateJpaRepository
import org.springframework.stereotype.Component

/**
 * 이미지 템플릿(image_templates) 엔티티의 out-port 어댑터. (엔티티당 어댑터 하나)
 * 팝업 등 소비처가 참조하는 코드로 행을 만들거나(신규) 이미지(URL·치수)를 교체한다(업서트).
 * 저장 URL은 앱 클라이언트가 그대로 로드할 수 있도록 공개 프록시(/images/{key})의 절대 URL로 만든다.
 * (버킷이 비공개라 직접 S3 URL 대신 presigned 리다이렉트 프록시를 거친다)
 */
@Component
class ImageTemplateAdapter(
	private val imageTemplateJpaRepository: ImageTemplateJpaRepository,
	private val imageProxyProperties: ImageProxyProperties,
) : SavePopupImageTemplatePort {

	override fun upsert(code: String, imageKey: String, imageWidth: Int, imageHeight: Int) {
		val imageUrl: String = "${imageProxyProperties.publicBaseUrl}/images/$imageKey"
		val existing: ImageTemplateEntity? = imageTemplateJpaRepository.findByCode(code)
		if (existing != null) {
			existing.imageUrl = imageUrl
			existing.imageWidth = imageWidth
			existing.imageHeight = imageHeight
			imageTemplateJpaRepository.save(existing)
			return
		}
		imageTemplateJpaRepository.save(
			ImageTemplateEntity(code = code, imageUrl = imageUrl, imageWidth = imageWidth, imageHeight = imageHeight),
		)
	}
}
