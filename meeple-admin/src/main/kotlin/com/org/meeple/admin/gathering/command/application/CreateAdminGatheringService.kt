package com.org.meeple.admin.gathering.command.application

import com.org.meeple.admin.common.time.TimeGenerator
import com.org.meeple.admin.gathering.command.application.port.`in`.CreateAdminGatheringUseCase
import com.org.meeple.admin.gathering.command.application.port.`in`.command.CreateAdminGatheringCommand
import com.org.meeple.admin.gathering.command.application.port.out.SaveAdminGatheringPort
import com.org.meeple.admin.gathering.command.application.port.out.UploadGatheringImagePort
import com.org.meeple.admin.gathering.command.domain.AdminGathering
import com.org.meeple.admin.gathering.command.domain.GatheringFee
import com.org.meeple.admin.gathering.command.domain.GatheringImage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * [CreateAdminGatheringUseCase] 구현. [command]로 모임([AdminGathering])을 만들어 저장한다. (운영 생성 → user_id null)
 * 대표 이미지가 있으면 검증([GatheringImage.validate]) 후 S3에 올리고([UploadGatheringImagePort]) 오브젝트 키만 저장한다.
 * (잘못된 파일이 S3에 올라가지 않도록 업로드 전에 검증한다) 모임 일시의 미래 여부는 [timeGenerator]를 기준으로 도메인이 검증한다.
 */
@Service
@Transactional
class CreateAdminGatheringService(
	private val saveAdminGatheringPort: SaveAdminGatheringPort,
	private val uploadGatheringImagePort: UploadGatheringImagePort,
	private val timeGenerator: TimeGenerator,
) : CreateAdminGatheringUseCase {

	override fun create(command: CreateAdminGatheringCommand): AdminGathering =
		saveAdminGatheringPort.save(
			AdminGathering.create(
				type = command.type,
				title = command.title,
				description = command.description,
				imageKey = uploadImageIfPresent(command),
				region = command.region,
				gatheringAt = command.gatheringAt,
				capacity = command.capacity,
				fee = GatheringFee(command.maleFee, command.femaleFee),
				earlyBirdFee = GatheringFee.optional(command.earlyBirdMaleFee, command.earlyBirdFemaleFee),
				discountFee = GatheringFee.optional(command.discountMaleFee, command.discountFemaleFee),
				now = timeGenerator.now(),
			),
		)

	// 대표 이미지가 없으면 null. 있으면 검증 후 S3에 올리고 저장에 쓸 오브젝트 키를 돌려준다.
	private fun uploadImageIfPresent(command: CreateAdminGatheringCommand): String? {
		val content: ByteArray = command.imageContent ?: return null
		GatheringImage.validate(command.imageContentType, command.imageSize)
		val contentType: String = command.imageContentType!!
		val key: String = "$KEY_PREFIX/${UUID.randomUUID()}.${GatheringImage.extensionOf(contentType)}"
		return uploadGatheringImagePort.upload(key, content, contentType)
	}

	companion object {
		private const val KEY_PREFIX: String = "gatherings"
	}
}
