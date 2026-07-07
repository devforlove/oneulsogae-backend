package com.org.meeple.infra.inquiry.query

import com.org.meeple.admin.inquiry.query.dao.GetAdminInquiryDao
import com.org.meeple.admin.inquiry.query.dto.AdminInquiryDetailView
import com.org.meeple.admin.inquiry.query.dto.AdminInquiryView
import com.org.meeple.admin.inquiry.query.dto.AdminInquiryViews
import com.org.meeple.common.inquiry.InquiryStatus
import com.org.meeple.infra.inquiry.command.entity.QInquiryEntity
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetAdminInquiryDao]의 QueryDSL 구현. (조회 전용)
 * 문의를 접수 시각(created_at) 내림차순(동률이면 id 내림차순)으로 offset/limit 페이징해 read model에 직접 투영한다.
 * status가 주어지면 동등 필터를 적용한다. (soft delete 행은 @SQLRestriction으로 양쪽 쿼리에서 제외)
 * 저장/갱신 out-port는 [com.org.meeple.infra.inquiry.command.adapter.InquiryAdapter]가 따로 구현한다.
 */
@Component
class GetAdminInquiryDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetAdminInquiryDao {

	override fun findPage(offset: Long, limit: Int, status: InquiryStatus?): AdminInquiryViews {
		val inquiry: QInquiryEntity = QInquiryEntity.inquiryEntity
		val views: List<AdminInquiryView> = queryFactory
			.select(
				Projections.constructor(
					AdminInquiryView::class.java,
					inquiry.id,
					inquiry.category,
					inquiry.status,
					inquiry.email,
					inquiry.createdAt,
				),
			)
			.from(inquiry)
			.where(statusEq(inquiry, status))
			.orderBy(inquiry.createdAt.desc(), inquiry.id.desc())
			.offset(offset)
			.limit(limit.toLong())
			.fetch()
		return AdminInquiryViews(views)
	}

	override fun count(status: InquiryStatus?): Long {
		val inquiry: QInquiryEntity = QInquiryEntity.inquiryEntity
		return queryFactory
			.select(inquiry.count())
			.from(inquiry)
			.where(statusEq(inquiry, status))
			.fetchOne() ?: 0L
	}

	override fun findDetailById(id: Long): AdminInquiryDetailView? {
		val inquiry: QInquiryEntity = QInquiryEntity.inquiryEntity
		return queryFactory
			.select(
				Projections.constructor(
					AdminInquiryDetailView::class.java,
					inquiry.id,
					inquiry.userId,
					inquiry.category,
					inquiry.email,
					inquiry.message,
					inquiry.status,
					inquiry.answer,
					inquiry.answeredAt,
					inquiry.createdAt,
				),
			)
			.from(inquiry)
			.where(inquiry.id.eq(id))
			.fetchOne()
	}

	// status가 null이면 null을 반환 → QueryDSL where가 해당 조건을 무시(전체 조회).
	private fun statusEq(inquiry: QInquiryEntity, status: InquiryStatus?): BooleanExpression? =
		status?.let { inquiry.status.eq(it) }
}
