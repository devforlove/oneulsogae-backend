package com.org.meeple.infra.popup.query

import com.org.meeple.core.popup.query.dao.GetPublicPopupDao
import com.org.meeple.core.popup.query.dto.PopupView
import com.org.meeple.core.popup.query.dto.PopupViews
import com.org.meeple.infra.image.entity.QImageTemplateEntity
import com.org.meeple.infra.popup.command.entity.QPopupEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 전역(public) 팝업 조회 dao([GetPublicPopupDao])의 QueryDSL 구현. (조회 전용)
 * 모든 사용자에게 노출하는 전역 팝업(user_id is null) 중 [now] 기준 노출 기간 내인 팝업을 [PopupViews] read model에 직접 투영한다.
 * 정렬은 DB에서 하지 않고 read model([PopupViews.mergeOrdered])이 책임진다. (전역+개인 합본을 한 번에 정렬)
 * 개인 팝업 조회는 [GetPrivatePopupDaoImpl]가 따로 담당한다. (전역/개인의 추가 조건이 갈라질 수 있어 쿼리를 나눈다)
 * soft delete(deleted_at) 행은 [com.org.meeple.infra.popup.command.entity.PopupEntity]의 @SQLRestriction으로 제외된다.
 */
@Component
class GetPublicPopupDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetPublicPopupDao {

	override fun findVisible(now: LocalDateTime): PopupViews {
		val popup: QPopupEntity = QPopupEntity.popupEntity
		val template: QImageTemplateEntity = QImageTemplateEntity.imageTemplateEntity
		val views: List<PopupView> = queryFactory
			.select(
				Projections.constructor(
					PopupView::class.java,
					popup.id,
					popup.title,
					popup.description,
					popup.displayOrder,
					template.imageUrl,
					template.imageWidth,
					template.imageHeight,
					popup.linkUrl,
					popup.buttonText,
					popup.popUpType,
				),
			)
			.from(popup)
			.leftJoin(template).on(template.code.eq(popup.imageCode))
			.where(
				popup.userId.isNull,
				popup.exposedFrom.loe(now),
				popup.exposedTo.goe(now),
			)
			.fetch()
		return PopupViews(views)
	}
}
