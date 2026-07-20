package com.org.oneulsogae.admin.gathering.command.application

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.common.time.TimeGenerator
import com.org.oneulsogae.admin.gathering.command.application.port.`in`.CreateGatheringScheduleUseCase
import com.org.oneulsogae.admin.gathering.command.application.port.`in`.command.CreateGatheringScheduleCommand
import com.org.oneulsogae.admin.gathering.command.application.port.out.LoadAdminGatheringPort
import com.org.oneulsogae.admin.gathering.command.application.port.out.SaveGatheringProductPort
import com.org.oneulsogae.admin.gathering.command.application.port.out.SaveGatheringSchedulePort
import com.org.oneulsogae.admin.gathering.command.domain.AdminGathering
import com.org.oneulsogae.admin.gathering.command.domain.GatheringFee
import com.org.oneulsogae.admin.gathering.command.domain.GatheringSchedule
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [CreateGatheringScheduleUseCase] 구현. [command]로 모임 일정([GatheringSchedule])을 만들어 저장한다.
 * 대상 모임을 [loadAdminGatheringPort]로 로드해(없으면 GATHERING_NOT_FOUND) 정원(maxParticipants)을 얼리버드 인원 검증에 쓴다.
 * 시간 범위 검증은 [timeGenerator] 기준으로 도메인이 수행한다.
 * 일정 저장 후 발급된 id를 성별·티어별 상품에 채워 [saveGatheringProductPort]로 함께 저장한다.
 */
@Service
@Transactional
class CreateGatheringScheduleService(
	private val loadAdminGatheringPort: LoadAdminGatheringPort,
	private val saveGatheringSchedulePort: SaveGatheringSchedulePort,
	private val saveGatheringProductPort: SaveGatheringProductPort,
	private val timeGenerator: TimeGenerator,
) : CreateGatheringScheduleUseCase {

	override fun create(command: CreateGatheringScheduleCommand): GatheringSchedule {
		val gathering: AdminGathering = loadAdminGatheringPort.loadById(command.gatheringId)
			?: throw AdminException(AdminErrorCode.GATHERING_NOT_FOUND, "모임을 찾을 수 없습니다: ${command.gatheringId}")
		val schedule: GatheringSchedule = GatheringSchedule.create(
			gatheringId = command.gatheringId,
			startAt = command.startAt,
			endAt = command.endAt,
			fee = GatheringFee(command.maleFee, command.femaleFee),
			earlyBirdDiscountRate = command.earlyBirdDiscountRate,
			earlyBirdCapacity = command.earlyBirdCapacity,
			discountFee = GatheringFee.optional(command.discountMaleFee, command.discountFemaleFee),
			maxParticipants = gathering.maxParticipants,
			now = timeGenerator.now(),
		)
		val saved: GatheringSchedule = saveGatheringSchedulePort.save(schedule)
		// 일정 저장으로 발급된 id를 상품에 채워 함께 저장한다. (같은 트랜잭션 — 원자적)
		saveGatheringProductPort.saveAll(schedule.products.withScheduleId(saved.id))
		return saved
	}
}
