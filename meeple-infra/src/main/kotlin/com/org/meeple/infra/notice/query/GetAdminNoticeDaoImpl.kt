package com.org.meeple.infra.notice.query

import com.org.meeple.admin.notice.query.dao.GetAdminNoticeDao
import com.org.meeple.admin.notice.query.dto.AdminNoticeDetailView
import com.org.meeple.admin.notice.query.dto.AdminNoticeView
import com.org.meeple.admin.notice.query.dto.AdminNoticeViews
import com.org.meeple.infra.notice.command.entity.QNoticeEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetAdminNoticeDao]의 QueryDSL 구현. (조회 전용)
 * 공지를 저장 날짜(created_at) 내림차순(동률이면 id 내림차순)으로 offset/limit 페이징해 read model에 직접 투영한다.
 * (soft delete 행은 @SQLRestriction으로 양쪽 쿼리에서 제외)
 * 저장 out-port는 [com.org.meeple.infra.notice.command.adapter.NoticeAdapter]가 따로 구현한다.
 */
@Component
class GetAdminNoticeDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetAdminNoticeDao {

	override fun findPage(offset: Long, limit: Int): AdminNoticeViews {
		val notice: QNoticeEntity = QNoticeEntity.noticeEntity
		val views: List<AdminNoticeView> = queryFactory
			.select(
				Projections.constructor(
					AdminNoticeView::class.java,
					notice.id,
					notice.title,
					notice.createdAt,
				),
			)
			.from(notice)
			.orderBy(notice.createdAt.desc(), notice.id.desc())
			.offset(offset)
			.limit(limit.toLong())
			.fetch()
		return AdminNoticeViews(views)
	}

	override fun count(): Long {
		val notice: QNoticeEntity = QNoticeEntity.noticeEntity
		return queryFactory
			.select(notice.count())
			.from(notice)
			.fetchOne() ?: 0L
	}

	override fun findDetailById(id: Long): AdminNoticeDetailView? {
		val notice: QNoticeEntity = QNoticeEntity.noticeEntity
		return queryFactory
			.select(
				Projections.constructor(
					AdminNoticeDetailView::class.java,
					notice.id,
					notice.title,
					notice.description,
					notice.createdAt,
				),
			)
			.from(notice)
			.where(notice.id.eq(id))
			.fetchOne()
	}
}
