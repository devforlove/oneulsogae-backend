package com.org.meeple.infra.gathering.command.adapter

import com.org.meeple.admin.memberverification.command.application.port.out.SaveGatheringProfilePort
import com.org.meeple.infra.gathering.command.entity.GatheringProfileEntity
import com.org.meeple.infra.gathering.command.repository.GatheringProfileJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 모임 프로필(gathering_profile) 엔티티의 out-port 어댑터. (엔티티당 어댑터 하나)
 * admin 멤버 인증 승인의 [SaveGatheringProfilePort]를 구현한다 — 유저당 1건 upsert(있으면 갱신, 없으면 생성).
 */
@Component
class GatheringProfileAdapter(
	private val gatheringProfileJpaRepository: GatheringProfileJpaRepository,
) : SaveGatheringProfilePort {

	override fun save(userId: Long, jobCategory: String, jobDetail: String, birthday: LocalDate?, height: Int?) {
		val entity: GatheringProfileEntity = gatheringProfileJpaRepository.findByUserId(userId)
			?.also { existing: GatheringProfileEntity ->
				existing.jobCategory = jobCategory
				existing.jobDetail = jobDetail
				existing.birthday = birthday
				existing.height = height
			}
			?: GatheringProfileEntity(
				userId = userId,
				jobCategory = jobCategory,
				jobDetail = jobDetail,
				birthday = birthday,
				height = height,
			)
		gatheringProfileJpaRepository.save(entity)
	}
}
