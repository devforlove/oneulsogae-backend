package com.org.meeple.infra.popup.query

import com.org.meeple.core.popup.query.dao.GetPrivatePopupDao
import com.org.meeple.core.popup.query.dto.PopupView
import com.org.meeple.core.popup.query.dto.PopupViews
import com.org.meeple.infra.image.entity.QImageTemplateEntity
import com.org.meeple.infra.popup.command.entity.QPopupEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 개인(private) 팝업 조회 dao([GetPrivatePopupDao])의 QueryDSL 구현. (조회 전용)
 * [userId]에게만 노출하는 개인 팝업(user_id = userId) 중 [now] 기준 노출 기간 내인 팝업을 [PopupViews] read model에 직접 투영한다.
 * 정렬은 DB에서 하지 않고 read model([PopupViews.mergeOrdered])이 책임진다. (전역+개인 합본을 한 번에 정렬)
 * 전역 팝업 조회는 [GetPublicPopupDaoImpl]가 따로 담당한다. (전역/개인의 추가 조건이 갈라질 수 있어 쿼리를 나눈다)
 * soft delete(deleted_at) 행은 [com.org.meeple.infra.popup.command.entity.PopupEntity]의 @SQLRestriction으로 제외된다.
 */
@Component
class GetPrivatePopupDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetPrivatePopupDao {

	override fun findVisible(now: LocalDateTime, userId: Long): PopupViews {
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
				popup.userId.eq(userId),
				popup.exposedFrom.loe(now),
				popup.exposedTo.goe(now),
			)
			.fetch()
		return PopupViews(views)
	}
}
