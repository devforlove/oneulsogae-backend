package com.org.meeple.infra.image.query

import com.org.meeple.core.image.query.dao.GetImageTemplateDao
import com.org.meeple.core.image.query.dto.ImageTemplateView
import com.org.meeple.infra.image.entity.QImageTemplateEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetImageTemplateDao]의 QueryDSL 구현. (조회 전용)
 * [code]로 image_templates를 단건 조회해 [ImageTemplateView]로 투영한다. (없으면 null)
 */
@Component
class GetImageTemplateDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetImageTemplateDao {

	override fun findByCode(code: String): ImageTemplateView? {
		val template: QImageTemplateEntity = QImageTemplateEntity.imageTemplateEntity
		return queryFactory
			.select(
				Projections.constructor(
					ImageTemplateView::class.java,
					template.code,
					template.imageUrl,
					template.imageWidth,
					template.imageHeight,
				),
			)
			.from(template)
			.where(template.code.eq(code))
			.fetchOne()
	}
}
