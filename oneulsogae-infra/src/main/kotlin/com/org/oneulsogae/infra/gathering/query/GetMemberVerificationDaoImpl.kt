package com.org.oneulsogae.infra.gathering.query

import com.org.oneulsogae.core.gathering.query.dao.GetMemberVerificationDao
import com.org.oneulsogae.core.gathering.query.dto.MemberVerificationView
import com.org.oneulsogae.infra.gathering.command.entity.MemberVerificationEntity
import com.org.oneulsogae.infra.gathering.command.repository.MemberVerificationJpaRepository
import org.springframework.stereotype.Component

/**
 * [GetMemberVerificationDao]의 구현. (조회 전용)
 * 단건·단일 테이블 조회라 Spring Data 파생 쿼리([MemberVerificationJpaRepository])로 최신 제출을 찾아
 * [MemberVerificationView] read model로 투영한다. (@SQLRestriction으로 soft-delete 행 제외)
 */
@Component
class GetMemberVerificationDaoImpl(
	private val memberVerificationJpaRepository: MemberVerificationJpaRepository,
) : GetMemberVerificationDao {

	override fun findLatestByUserId(userId: Long): MemberVerificationView? {
		val entity: MemberVerificationEntity =
			memberVerificationJpaRepository.findFirstByUserIdOrderByIdDesc(userId) ?: return null
		return MemberVerificationView(
			id = entity.id ?: 0,
			status = entity.status,
			jobCategory = entity.jobCategory,
			jobDetail = entity.jobDetail,
			rejectionReason = entity.rejectionReason,
		)
	}
}
