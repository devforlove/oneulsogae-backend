package com.org.oneulsogae.infra.gathering.command.adapter

import com.org.oneulsogae.admin.gathering.command.application.port.out.ExistsGatheringProfilePort
import com.org.oneulsogae.admin.memberverification.command.application.port.out.SaveGatheringProfilePort
import com.org.oneulsogae.core.gathering.command.application.port.out.UpdateGatheringProfilePort
import com.org.oneulsogae.infra.gathering.command.entity.GatheringProfileEntity
import com.org.oneulsogae.infra.gathering.command.repository.GatheringProfileJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 모임 프로필(gathering_profile) 엔티티의 out-port 어댑터. (엔티티당 어댑터 하나)
 * admin 멤버 인증 승인의 [SaveGatheringProfilePort](유저당 1건 upsert)와,
 * 프로필 변경 동기화의 [UpdateGatheringProfilePort](유저 유래 필드 최신화),
 * 회원 인증 여부 조회 [ExistsGatheringProfilePort]를 함께 구현한다.
 */
@Component
class GatheringProfileAdapter(
	private val gatheringProfileJpaRepository: GatheringProfileJpaRepository,
) : SaveGatheringProfilePort, UpdateGatheringProfilePort, ExistsGatheringProfilePort {

	override fun save(
		userId: Long,
		jobCategory: String,
		jobDetail: String,
		birthday: LocalDate?,
		height: Int?,
		profileImageCode: String?,
	) {
		val entity: GatheringProfileEntity = gatheringProfileJpaRepository.findByUserId(userId)
			?.also { existing: GatheringProfileEntity ->
				existing.jobCategory = jobCategory
				existing.jobDetail = jobDetail
				existing.birthday = birthday
				existing.height = height
				existing.profileImageCode = profileImageCode
			}
			?: GatheringProfileEntity(
				userId = userId,
				jobCategory = jobCategory,
				jobDetail = jobDetail,
				birthday = birthday,
				height = height,
				profileImageCode = profileImageCode,
			)
		gatheringProfileJpaRepository.save(entity)
	}

	// 프로필 변경 동기화: gathering_profile 행이 있으면 유저 유래 필드를 최신화한다. 없으면(미승인) no-op.
	override fun updateUserFields(userId: Long, profileImageCode: String?, birthday: LocalDate?, height: Int?) {
		val entity: GatheringProfileEntity = gatheringProfileJpaRepository.findByUserId(userId) ?: return
		entity.profileImageCode = profileImageCode
		entity.birthday = birthday
		entity.height = height
		gatheringProfileJpaRepository.save(entity)
	}

	// 회원 인증 여부: gathering_profile 행 존재 여부.
	override fun existsByUserId(userId: Long): Boolean =
		gatheringProfileJpaRepository.existsByUserId(userId)
}
