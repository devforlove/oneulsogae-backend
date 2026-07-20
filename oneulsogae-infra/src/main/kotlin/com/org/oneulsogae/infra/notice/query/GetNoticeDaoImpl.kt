package com.org.oneulsogae.infra.notice.query

import com.org.oneulsogae.core.notice.query.dao.GetNoticeDao
import com.org.oneulsogae.core.notice.query.dto.NoticeView
import com.org.oneulsogae.core.notice.query.dto.NoticeViews
import com.org.oneulsogae.infra.notice.command.entity.QNoticeEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * 공지 조회 dao([GetNoticeDao])의 QueryDSL 구현. (조회 전용)
 * 공지를 저장 날짜(created_at) 내림차순(동률이면 id 내림차순)으로 limit/offset 페이징해 [NoticeViews] read model에 직접 투영한다.
 * 전체 개수([count])는 페이지 메타데이터 계산에 쓴다. (soft delete 행은 @SQLRestriction으로 양쪽 쿼리에서 제외)
 * 저장 out-port([com.org.oneulsogae.core.notice.command.application.port.out.SaveNoticePort])는 [com.org.oneulsogae.infra.notice.command.adapter.NoticeAdapter]가 따로 구현한다.
 */
@Component
class GetNoticeDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetNoticeDao {

	override fun findPage(offset: Long, limit: Int): NoticeViews {
		val notice: QNoticeEntity = QNoticeEntity.noticeEntity
		val views: List<NoticeView> = queryFactory
			.select(
				Projections.constructor(
					NoticeView::class.java,
					notice.id,
					notice.title,
					notice.description,
					notice.createdAt,
				),
			)
			.from(notice)
			.orderBy(notice.createdAt.desc(), notice.id.desc())
			.offset(offset)
			.limit(limit.toLong())
			.fetch()
		return NoticeViews(views)
	}

	override fun count(): Long {
		val notice: QNoticeEntity = QNoticeEntity.noticeEntity
		return queryFactory
			.select(notice.count())
			.from(notice)
			.fetchOne() ?: 0L
	}
}
