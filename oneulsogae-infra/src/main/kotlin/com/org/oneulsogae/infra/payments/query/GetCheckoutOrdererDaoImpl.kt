package com.org.oneulsogae.infra.payments.query

import com.org.oneulsogae.core.payments.query.dao.GetCheckoutOrdererDao
import com.org.oneulsogae.core.payments.query.dto.OrdererView
import com.org.oneulsogae.core.user.command.domain.IdentityVerificationStatus
import com.org.oneulsogae.infra.user.command.entity.QIdentityVerificationEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import com.querydsl.core.Tuple
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * 체크아웃 주문자 정보 조회 dao([GetCheckoutOrdererDao])의 QueryDSL 구현.
 * user 계열 엔티티를 [OrdererView]로 직접 투영한다. (표시용 조인 패턴 — user 도메인 포트를 거치지 않는다)
 * 실명(최신 VERIFIED 1건)은 상관 서브쿼리 조인 대신 별도 쿼리로 읽는다.
 * (① users PK seek ⟕ user_details ux_user_id, ② identity_verifications idx_iv_user_id + id desc — 둘 다 인덱스 seek)
 */
@Component
class GetCheckoutOrdererDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetCheckoutOrdererDao {

	override fun findOrdererByUserId(userId: Long): OrdererView? {
		val user: QUserEntity = QUserEntity.userEntity
		val userDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val identity: QIdentityVerificationEntity = QIdentityVerificationEntity.identityVerificationEntity

		val base: Tuple = queryFactory
			.select(user.email, userDetail.phoneNumber)
			.from(user)
			.leftJoin(userDetail).on(userDetail.userId.eq(user.id))
			.where(user.id.eq(userId))
			.fetchOne() ?: return null

		val realName: String? = queryFactory
			.select(identity.realName)
			.from(identity)
			.where(
				identity.userId.eq(userId),
				identity.status.eq(IdentityVerificationStatus.VERIFIED),
			)
			.orderBy(identity.id.desc())
			.fetchFirst()

		return OrdererView(
			name = realName,
			email = base.get(user.email),
			phoneNumber = base.get(userDetail.phoneNumber),
		)
	}
}
