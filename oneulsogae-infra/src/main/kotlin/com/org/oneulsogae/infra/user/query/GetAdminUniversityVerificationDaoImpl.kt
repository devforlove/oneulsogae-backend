package com.org.oneulsogae.infra.user.query

import com.org.oneulsogae.admin.universityverification.query.dao.GetAdminUniversityVerificationDao
import com.org.oneulsogae.admin.universityverification.query.dto.AdminUniversityVerificationDetailView
import com.org.oneulsogae.admin.universityverification.query.dto.AdminUniversityVerificationView
import com.org.oneulsogae.admin.universityverification.query.dto.AdminUniversityVerificationViews
import com.org.oneulsogae.common.user.UniversityImageVerificationStatus
import com.org.oneulsogae.infra.user.command.entity.QUniversityImageVerificationEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetAdminUniversityVerificationDao]의 QueryDSL 구현. (조회 전용)
 * university_image_verifications를 최신순(id desc)으로 페이징하고,
 * user_details(nickname)·users(email)를 userId로 leftJoin해 표시 정보를 채운다.
 * (@SQLRestriction으로 soft-delete 행은 자동 제외. status 필터는 있으면 동등 조건, 없으면 전체)
 */
@Component
class GetAdminUniversityVerificationDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetAdminUniversityVerificationDao {

	override fun findPage(
		offset: Long,
		limit: Int,
		status: UniversityImageVerificationStatus?,
	): AdminUniversityVerificationViews {
		val verification: QUniversityImageVerificationEntity = QUniversityImageVerificationEntity.universityImageVerificationEntity
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val user: QUserEntity = QUserEntity.userEntity

		val views: List<AdminUniversityVerificationView> = queryFactory
			.select(
				Projections.constructor(
					AdminUniversityVerificationView::class.java,
					verification.id,
					verification.userId,
					detail.nickname,
					user.email,
					verification.status,
					verification.createdAt,
					verification.imageKey,
				),
			)
			.from(verification)
			.leftJoin(detail).on(detail.userId.eq(verification.userId))
			.leftJoin(user).on(user.id.eq(verification.userId))
			.where(statusEq(verification, status))
			.orderBy(verification.id.desc())
			.offset(offset)
			.limit(limit.toLong())
			.fetch()
		return AdminUniversityVerificationViews(views)
	}

	override fun count(status: UniversityImageVerificationStatus?): Long {
		val verification: QUniversityImageVerificationEntity = QUniversityImageVerificationEntity.universityImageVerificationEntity
		return queryFactory
			.select(verification.count())
			.from(verification)
			.where(statusEq(verification, status))
			.fetchOne() ?: 0L
	}

	override fun findDetailById(id: Long): AdminUniversityVerificationDetailView? {
		val verification: QUniversityImageVerificationEntity = QUniversityImageVerificationEntity.universityImageVerificationEntity
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val user: QUserEntity = QUserEntity.userEntity

		return queryFactory
			.select(
				Projections.constructor(
					AdminUniversityVerificationDetailView::class.java,
					verification.id,
					verification.userId,
					detail.nickname,
					user.email,
					verification.status,
					verification.createdAt,
					verification.imageKey,
					verification.previousUniversityName,
					detail.universityEmail,
					verification.universityName,
					verification.rejectionReason,
				),
			)
			.from(verification)
			.leftJoin(detail).on(detail.userId.eq(verification.userId))
			.leftJoin(user).on(user.id.eq(verification.userId))
			.where(verification.id.eq(id))
			.fetchOne()
	}

	/** status가 있으면 동등 조건, 없으면 null(=where 무시). */
	private fun statusEq(
		verification: QUniversityImageVerificationEntity,
		status: UniversityImageVerificationStatus?,
	): BooleanExpression? =
		status?.let { verification.status.eq(it) }
}
