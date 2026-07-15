package com.org.meeple.infra.payments.query

import com.org.meeple.core.payments.query.dao.GetPaymentMethodDao
import com.org.meeple.core.payments.query.dto.PaymentMethodView
import com.org.meeple.core.payments.query.dto.PaymentMethodViews
import com.org.meeple.infra.payments.command.entity.QPaymentMethodEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * 결제수단 조회 dao([GetPaymentMethodDao])의 QueryDSL 구현.
 * 활성 수단만 노출 순서대로 [PaymentMethodView]로 직접 투영한다. (참조 데이터 전행 조회라 인덱스 불필요)
 */
@Component
class GetPaymentMethodDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetPaymentMethodDao {

	override fun findActiveMethods(): PaymentMethodViews {
		val paymentMethod: QPaymentMethodEntity = QPaymentMethodEntity.paymentMethodEntity
		val views: List<PaymentMethodView> = queryFactory
			.select(Projections.constructor(PaymentMethodView::class.java, paymentMethod.code, paymentMethod.name))
			.from(paymentMethod)
			.where(paymentMethod.active.isTrue)
			.orderBy(paymentMethod.displayOrder.asc(), paymentMethod.id.asc())
			.fetch()
		return PaymentMethodViews(values = views)
	}
}
