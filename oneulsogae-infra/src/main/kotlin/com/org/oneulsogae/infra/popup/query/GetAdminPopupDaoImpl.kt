package com.org.oneulsogae.infra.popup.query

import com.org.oneulsogae.admin.popup.query.dao.GetAdminPopupDao
import com.org.oneulsogae.admin.popup.query.dto.AdminPopupDetailView
import com.org.oneulsogae.admin.popup.query.dto.AdminPopupView
import com.org.oneulsogae.admin.popup.query.dto.AdminPopupViews
import com.org.oneulsogae.infra.popup.command.entity.QPopupEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetAdminPopupDao]의 QueryDSL 구현. (조회 전용)
 * 전역 팝업(user_id is null)만 노출 순서(display_order asc, 동률이면 id desc)로 offset/limit 페이징해 read model에 직접 투영한다.
 * (soft delete 행은 @SQLRestriction으로 모든 쿼리에서 제외. 팝업은 어드민이 관리하는 소량 데이터라 offset 스캔로 충분하다)
 * 저장 out-port는 [com.org.oneulsogae.infra.popup.command.adapter.PopupAdapter]가 따로 구현한다.
 */
@Component
class GetAdminPopupDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetAdminPopupDao {

	override fun findPage(offset: Long, limit: Int): AdminPopupViews {
		val popup: QPopupEntity = QPopupEntity.popupEntity
		val views: List<AdminPopupView> = queryFactory
			.select(
				Projections.constructor(
					AdminPopupView::class.java,
					popup.id,
					popup.title,
					popup.displayOrder,
					popup.popUpType,
					popup.exposedFrom,
					popup.exposedTo,
					popup.createdAt,
				),
			)
			.from(popup)
			.where(popup.userId.isNull)
			.orderBy(popup.displayOrder.asc(), popup.id.desc())
			.offset(offset)
			.limit(limit.toLong())
			.fetch()
		return AdminPopupViews(views)
	}

	override fun count(): Long {
		val popup: QPopupEntity = QPopupEntity.popupEntity
		return queryFactory
			.select(popup.count())
			.from(popup)
			.where(popup.userId.isNull)
			.fetchOne() ?: 0L
	}

	override fun findDetailById(id: Long): AdminPopupDetailView? {
		val popup: QPopupEntity = QPopupEntity.popupEntity
		return queryFactory
			.select(
				Projections.constructor(
					AdminPopupDetailView::class.java,
					popup.id,
					popup.title,
					popup.description,
					popup.displayOrder,
					popup.imageCode,
					popup.linkUrl,
					popup.buttonText,
					popup.popUpType,
					popup.exposedFrom,
					popup.exposedTo,
					popup.createdAt,
				),
			)
			.from(popup)
			.where(popup.id.eq(id), popup.userId.isNull)
			.fetchOne()
	}
}
