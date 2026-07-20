package com.org.oneulsogae.infra.gathering.query

import com.org.oneulsogae.admin.memberverification.query.dao.GetAdminMemberVerificationDao
import com.org.oneulsogae.admin.memberverification.query.dto.AdminMemberVerificationDetailView
import com.org.oneulsogae.admin.memberverification.query.dto.AdminMemberVerificationView
import com.org.oneulsogae.admin.memberverification.query.dto.AdminMemberVerificationViews
import com.org.oneulsogae.common.gathering.MemberVerificationStatus
import com.org.oneulsogae.infra.gathering.command.entity.QMemberVerificationEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetAdminMemberVerificationDao]의 QueryDSL 구현. (조회 전용)
 * member_verifications를 최신순(id desc)으로 페이징하고,
 * user_details(nickname)·users(email)를 userId로 leftJoin해 표시 정보를 채운다.
 * (@SQLRestriction으로 soft-delete 행은 자동 제외. status 필터는 있으면 동등 조건, 없으면 전체)
 */
@Component
class GetAdminMemberVerificationDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetAdminMemberVerificationDao {

	override fun findPage(
		offset: Long,
		limit: Int,
		status: MemberVerificationStatus?,
	): AdminMemberVerificationViews {
		val verification: QMemberVerificationEntity = QMemberVerificationEntity.memberVerificationEntity
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val user: QUserEntity = QUserEntity.userEntity

		val views: List<AdminMemberVerificationView> = queryFactory
			.select(
				Projections.constructor(
					AdminMemberVerificationView::class.java,
					verification.id,
					verification.userId,
					detail.nickname,
					user.email,
					verification.status,
					verification.jobCategory,
					verification.createdAt,
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
		return AdminMemberVerificationViews(views)
	}

	override fun count(status: MemberVerificationStatus?): Long {
		val verification: QMemberVerificationEntity = QMemberVerificationEntity.memberVerificationEntity
		return queryFactory
			.select(verification.count())
			.from(verification)
			.where(statusEq(verification, status))
			.fetchOne() ?: 0L
	}

	override fun findDetailById(id: Long): AdminMemberVerificationDetailView? {
		val verification: QMemberVerificationEntity = QMemberVerificationEntity.memberVerificationEntity
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val user: QUserEntity = QUserEntity.userEntity

		return queryFactory
			.select(
				Projections.constructor(
					AdminMemberVerificationDetailView::class.java,
					verification.id,
					verification.userId,
					detail.nickname,
					user.email,
					verification.status,
					verification.jobCategory,
					verification.jobDetail,
					verification.rejectionReason,
					verification.createdAt,
					verification.faceImageKey,
					verification.idCardImageKey,
					verification.documentImageKey,
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
		verification: QMemberVerificationEntity,
		status: MemberVerificationStatus?,
	): BooleanExpression? =
		status?.let { verification.status.eq(it) }
}
