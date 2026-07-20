package com.org.oneulsogae.infra.report.query

import com.org.oneulsogae.admin.report.query.dao.GetAdminReportDao
import com.org.oneulsogae.admin.report.query.dto.AdminReportDetailView
import com.org.oneulsogae.admin.report.query.dto.AdminReportSummaryView
import com.org.oneulsogae.admin.report.query.dto.AdminReportSummaryViews
import com.org.oneulsogae.infra.report.command.entity.QReportEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetAdminReportDao]의 QueryDSL 구현. (조회 전용)
 * 유저 신고(to_user_id 존재)만 최신순(created_at desc, id desc)으로 페이징하고,
 * 신고자(from_user_id)·대상(to_user_id)의 표시 정보를 users·user_details 각각 별칭 leftJoin으로 채운다.
 * (@SQLRestriction으로 soft-delete 행은 report·user·user_detail 모두 자동 제외)
 */
@Component
class GetAdminReportDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetAdminReportDao {

	override fun findPage(offset: Long, limit: Int): AdminReportSummaryViews {
		val report: QReportEntity = QReportEntity.reportEntity
		val reporterUser = QUserEntity("reporterUser")
		val reporterDetail = QUserDetailEntity("reporterDetail")
		val targetUser = QUserEntity("targetUser")
		val targetDetail = QUserDetailEntity("targetDetail")

		val views: List<AdminReportSummaryView> = queryFactory
			.select(
				Projections.constructor(
					AdminReportSummaryView::class.java,
					report.id,
					report.type,
					report.status,
					report.createdAt,
					report.fromUserId,
					reporterDetail.nickname,
					reporterUser.email,
					report.toUserId,
					targetDetail.nickname,
					targetUser.email,
				),
			)
			.from(report)
			.leftJoin(reporterUser).on(reporterUser.id.eq(report.fromUserId))
			.leftJoin(reporterDetail).on(reporterDetail.userId.eq(report.fromUserId))
			.leftJoin(targetUser).on(targetUser.id.eq(report.toUserId))
			.leftJoin(targetDetail).on(targetDetail.userId.eq(report.toUserId))
			.where(report.toUserId.isNotNull)
			.orderBy(report.createdAt.desc(), report.id.desc())
			.offset(offset)
			.limit(limit.toLong())
			.fetch()
		return AdminReportSummaryViews(views)
	}

	override fun count(): Long {
		val report: QReportEntity = QReportEntity.reportEntity
		return queryFactory
			.select(report.count())
			.from(report)
			.where(report.toUserId.isNotNull)
			.fetchOne() ?: 0L
	}

	override fun findDetailById(id: Long): AdminReportDetailView? {
		val report: QReportEntity = QReportEntity.reportEntity
		val reporterUser = QUserEntity("reporterUser")
		val reporterDetail = QUserDetailEntity("reporterDetail")
		val targetUser = QUserEntity("targetUser")
		val targetDetail = QUserDetailEntity("targetDetail")

		return queryFactory
			.select(
				Projections.constructor(
					AdminReportDetailView::class.java,
					report.id,
					report.type,
					report.status,
					report.createdAt,
					report.fromUserId,
					reporterDetail.nickname,
					reporterUser.email,
					report.toUserId,
					targetDetail.nickname,
					targetUser.email,
					report.description,
					report.chatRoomId,
				),
			)
			.from(report)
			.leftJoin(reporterUser).on(reporterUser.id.eq(report.fromUserId))
			.leftJoin(reporterDetail).on(reporterDetail.userId.eq(report.fromUserId))
			.leftJoin(targetUser).on(targetUser.id.eq(report.toUserId))
			.leftJoin(targetDetail).on(targetDetail.userId.eq(report.toUserId))
			.where(report.id.eq(id), report.toUserId.isNotNull)
			.fetchOne()
	}
}
