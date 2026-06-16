package com.org.meeple.infra.user.query

import com.org.meeple.core.user.query.dao.UserDetailQueryDao
import com.org.meeple.core.user.query.dto.UserDetailView
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.UserDetailEntity
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [UserDetailQueryDao]의 QueryDSL 구현체. (조회 전용)
 * traits/interests가 JSON 컨버터 컬럼이라 Projections 대신 엔티티를 가져와 read model로 투영한다.
 * 명령 흐름의 단건 로드는 command 쪽 [com.org.meeple.infra.user.command.adapter.UserDetailCoreAdapter]가 메서드 쿼리로 따로 구현한다.
 */
@Component
class UserDetailQueryDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : UserDetailQueryDao {

	override fun findByUserId(userId: Long): UserDetailView? {
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		return queryFactory
			.selectFrom(detail)
			.where(detail.userId.eq(userId))
			.fetchOne()
			?.toView()
	}
}

/** [UserDetailEntity] → 조회 read model([UserDetailView]) 투영. (query 전용) */
internal fun UserDetailEntity.toView(): UserDetailView =
	UserDetailView(
		id = id ?: 0,
		userId = userId,
		nickname = nickname,
		profileImageCode = profileImageCode,
		age = age,
		height = height,
		gender = gender,
		phoneNumber = phoneNumber,
		job = job,
		activityArea = activityArea,
		regionCode = regionCode,
		introduction = introduction,
		traits = traits,
		interests = interests,
		companyEmail = companyEmail,
		companyName = companyName,
		maritalStatus = maritalStatus,
		smokingStatus = smokingStatus,
		religion = religion,
		drinkingStatus = drinkingStatus,
		bodyType = bodyType,
	)
