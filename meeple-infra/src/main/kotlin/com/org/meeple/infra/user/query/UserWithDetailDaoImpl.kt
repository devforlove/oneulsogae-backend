package com.org.meeple.infra.user.query

import com.org.meeple.core.user.query.dao.UserWithDetailDao
import com.org.meeple.core.user.query.dto.UserView
import com.org.meeple.core.user.query.dto.UserWithDetailView
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.infra.user.command.entity.UserDetailEntity
import com.org.meeple.infra.user.command.entity.UserEntity
import com.querydsl.core.Tuple
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [UserWithDetailDao]의 QueryDSL 구현체. (조회 전용)
 * 사용자와 프로필 상세를 명시적 조인으로 한 번에 가져와(1+N 방지) 평탄 read model([UserWithDetailView])로 투영한다.
 */
@Component
class UserWithDetailDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : UserWithDetailDao {

	override fun findWithDetailByUserId(userId: Long): UserWithDetailView? {
		val user: QUserEntity = QUserEntity.userEntity
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity

		val row: Tuple = queryFactory
			.select(user, detail)
			.from(user)
			.join(detail).on(detail.userId.eq(user.id))
			.where(user.id.eq(userId))
			.fetchOne()
			?: return null

		val userEntity: UserEntity = row.get(user)!!
		val detailEntity: UserDetailEntity = row.get(detail)!!
		return UserWithDetailView(
			user = UserView(
				id = userEntity.id ?: 0,
				email = userEntity.email,
				status = userEntity.status,
			),
			detail = detailEntity.toView(),
		)
	}
}
