package com.org.meeple.admin.gathering.command.application

import com.org.meeple.admin.common.time.TimeGenerator
import com.org.meeple.admin.gathering.command.application.port.`in`.CreateAdminGatheringUseCase
import com.org.meeple.admin.gathering.command.application.port.`in`.command.CreateAdminGatheringCommand
import com.org.meeple.admin.gathering.command.application.port.out.SaveAdminGatheringPort
import com.org.meeple.admin.gathering.command.domain.AdminGathering
import com.org.meeple.admin.gathering.command.domain.GatheringFee
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [CreateAdminGatheringUseCase] 구현. [command]로 모임([AdminGathering])을 만들어 저장한다. (운영 생성 → user_id null)
 * 모임 일시의 미래 여부는 [timeGenerator]의 현재 시각을 기준으로 도메인이 검증한다.
 */
@Service
@Transactional
class CreateAdminGatheringService(
	private val saveAdminGatheringPort: SaveAdminGatheringPort,
	private val timeGenerator: TimeGenerator,
) : CreateAdminGatheringUseCase {

	override fun create(command: CreateAdminGatheringCommand): AdminGathering =
		saveAdminGatheringPort.save(
			AdminGathering.create(
				type = command.type,
				title = command.title,
				description = command.description,
				imageUrl = command.imageUrl,
				region = command.region,
				gatheringAt = command.gatheringAt,
				capacity = command.capacity,
				fee = GatheringFee(command.maleFee, command.femaleFee),
				earlyBirdFee = GatheringFee.optional(command.earlyBirdMaleFee, command.earlyBirdFemaleFee),
				discountFee = GatheringFee.optional(command.discountMaleFee, command.discountFemaleFee),
				now = timeGenerator.now(),
			),
		)
}
