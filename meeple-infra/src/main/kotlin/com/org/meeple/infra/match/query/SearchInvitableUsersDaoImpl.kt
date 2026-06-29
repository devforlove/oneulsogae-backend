package com.org.meeple.infra.match.query

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.query.dao.SearchInvitableUsersDao
import com.org.meeple.core.match.query.dto.InvitableUser
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [SearchInvitableUsersDao]의 QueryDSL 구현체. (조회 전용)
 * 후보 베이스는 매칭 가능(match_user 존재)을 보장하는 [QMatchUserEntity]이고, 닉네임·성별도 동기화된 이 테이블에서 필터한다(복합 인덱스 idx_nickname_gender_user_id seek). 직업·회사명만 [QUserDetailEntity] 조인으로 가져온다.
 * 닉네임 정확 일치 + 같은 성별 + 자기 제외를 만족하는 유저를 userId 오름차순으로 투영한다.
 * (이미 활성 팀에 속한 유저는 검색에서 거르지 않는다 — 실제 초대 시점에 invite 명령이 ALREADY_IN_TEAM으로 차단한다)
 */
@Component
class SearchInvitableUsersDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : SearchInvitableUsersDao {

	override fun findRequesterGender(userId: Long): Gender? {
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		return queryFactory
			.select(matchUser.gender)
			.from(matchUser)
			.where(matchUser.userId.eq(userId))
			.fetchOne()
	}

	override fun search(requesterGender: Gender, userId: Long, nickname: String): List<InvitableUser> {
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		val userDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity

		return queryFactory
			.select(
				Projections.constructor(
					InvitableUser::class.java,
					matchUser.userId,
					matchUser.nickname,
					userDetail.job,
					userDetail.companyName,
					userDetail.universityName,
					matchUser.gender,
					matchUser.profileImageCode,
					matchUser.birthday,
				),
			)
			.from(matchUser)
			.join(userDetail).on(userDetail.userId.eq(matchUser.userId))
			.where(
				matchUser.nickname.eq(nickname),
				matchUser.gender.eq(requesterGender),
				matchUser.userId.ne(userId),
			)
			.orderBy(matchUser.userId.asc())
			.fetch()
	}
}
