package com.org.oneulsogae.admin.gathering.command.application

import com.org.oneulsogae.admin.gathering.command.application.port.`in`.UploadGatheringDescriptionImageUseCase
import com.org.oneulsogae.admin.gathering.command.application.port.out.UploadGatheringImagePort
import com.org.oneulsogae.admin.gathering.command.domain.GatheringDescriptionImage
import com.org.oneulsogae.admin.gathering.command.domain.GatheringImage
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * [UploadGatheringDescriptionImageUseCase] 구현. 대표 이미지 업로드와 동일한 검증([GatheringImage.validate])을
 * 거친 뒤, 소개 전용 프리픽스([GatheringDescriptionImage.KEY_PREFIX])로 오브젝트 키를 만들어 S3에 저장한다.
 */
@Service
class UploadGatheringDescriptionImageService(
	private val uploadGatheringImagePort: UploadGatheringImagePort,
) : UploadGatheringDescriptionImageUseCase {

	override fun execute(content: ByteArray?, contentType: String?, size: Long): String {
		GatheringImage.validate(contentType, size) // 실패 시 GATHER-009/010
		val key = "${GatheringDescriptionImage.KEY_PREFIX}/${UUID.randomUUID()}.${GatheringImage.extensionOf(contentType!!)}"
		uploadGatheringImagePort.upload(key, content!!, contentType)
		return key
	}
}
