package com.org.meeple.admin.gathering.command.application

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.common.time.TimeGenerator
import com.org.meeple.admin.gathering.command.application.port.`in`.UpdateAdminGatheringUseCase
import com.org.meeple.admin.gathering.command.application.port.`in`.command.UpdateAdminGatheringCommand
import com.org.meeple.admin.gathering.command.application.port.out.LoadAdminGatheringPort
import com.org.meeple.admin.gathering.command.application.port.out.UpdateAdminGatheringPort
import com.org.meeple.admin.gathering.command.application.port.out.UploadGatheringImagePort
import com.org.meeple.admin.gathering.command.domain.AdminGathering
import com.org.meeple.admin.gathering.command.domain.GatheringFee
import com.org.meeple.admin.gathering.command.domain.GatheringImage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * [UpdateAdminGatheringUseCase] 구현. (명령)
 * 대상 모임을 로드해 없으면 GATHERING_NOT_FOUND. 새 이미지가 오면 검증·업로드해 교체하고, 없으면 기존 이미지를 유지한다.
 * 전체 데이터를 [AdminGathering.update]로 교체(검증 포함)한 뒤 저장한다. (id·status·생성 시각은 보존)
 */
@Service
@Transactional
class UpdateAdminGatheringService(
	private val loadAdminGatheringPort: LoadAdminGatheringPort,
	private val updateAdminGatheringPort: UpdateAdminGatheringPort,
	private val uploadGatheringImagePort: UploadGatheringImagePort,
	private val timeGenerator: TimeGenerator,
) : UpdateAdminGatheringUseCase {

	override fun update(id: Long, command: UpdateAdminGatheringCommand) {
		val existing: AdminGathering = loadAdminGatheringPort.loadById(id)
			?: throw AdminException(AdminErrorCode.GATHERING_NOT_FOUND, "모임을 찾을 수 없습니다: $id")

		val updated: AdminGathering = existing.update(
			type = command.type,
			title = command.title,
			description = command.description,
			imageKey = resolveImageKey(command, existing.imageKey),
			region = command.region,
			gatheringAt = command.gatheringAt,
			minParticipants = command.minParticipants,
			maxParticipants = command.maxParticipants,
			fee = GatheringFee(command.maleFee, command.femaleFee),
			earlyBirdFee = GatheringFee.optional(command.earlyBirdMaleFee, command.earlyBirdFemaleFee),
			earlyBirdCapacity = command.earlyBirdCapacity,
			discountFee = GatheringFee.optional(command.discountMaleFee, command.discountFemaleFee),
			now = timeGenerator.now(),
		)
		updateAdminGatheringPort.update(updated)
	}

	// 새 이미지가 오면 검증 후 업로드해 새 키를, 없으면 기존 키([currentImageKey])를 유지한다.
	private fun resolveImageKey(command: UpdateAdminGatheringCommand, currentImageKey: String?): String? {
		val content: ByteArray = command.imageContent ?: return currentImageKey
		GatheringImage.validate(command.imageContentType, command.imageSize)
		val contentType: String = command.imageContentType!!
		val key: String = "$KEY_PREFIX/${UUID.randomUUID()}.${GatheringImage.extensionOf(contentType)}"
		return uploadGatheringImagePort.upload(key, content, contentType)
	}

	companion object {
		private const val KEY_PREFIX: String = "gatherings"
	}
}
