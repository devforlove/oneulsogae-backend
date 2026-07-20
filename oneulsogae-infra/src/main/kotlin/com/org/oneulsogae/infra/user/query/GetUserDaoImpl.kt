package com.org.oneulsogae.infra.user.query

import com.org.oneulsogae.core.user.query.dao.GetUserDao
import com.org.oneulsogae.core.user.query.dto.UserView
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetUserDao]의 QueryDSL 구현체. (조회 전용)
 * 명령 흐름의 단건 로드는 command 쪽 [com.org.oneulsogae.infra.user.command.adapter.UserRepositoryAdapter]가 메서드 쿼리로 따로 구현한다.
 */
@Component
class GetUserDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetUserDao {

	override fun findById(id: Long): UserView? {
		val user: QUserEntity = QUserEntity.userEntity
		return queryFactory
			.select(
				Projections.constructor(
					UserView::class.java,
					user.id,
					user.email,
					user.status,
				),
			)
			.from(user)
			.where(user.id.eq(id))
			.fetchOne()
	}
}
