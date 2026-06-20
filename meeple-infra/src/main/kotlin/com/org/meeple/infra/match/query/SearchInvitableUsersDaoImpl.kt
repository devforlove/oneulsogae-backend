package com.org.meeple.infra.match.query

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.query.dao.SearchInvitableUsersDao
import com.org.meeple.core.match.query.dto.InvitableUser
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [SearchInvitableUsersDao]의 QueryDSL 구현체. (조회 전용)
 * 후보 베이스는 매칭 가능(match_user 존재)을 보장하는 [QMatchUserEntity]이고, 표시 필드(닉네임·직업·회사명)는 [QUserDetailEntity] 조인으로 가져온다.
 * 닉네임 정확 일치 + 같은 성별 + 자기 제외 + 활성 팀 미소속([QTeamMemberEntity] NOT EXISTS)을 만족하는 유저를 userId 오름차순으로 투영한다.
 */
@Component
class SearchInvitableUsersDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : SearchInvitableUsersDao {

	override fun findRequesterGender(requesterId: Long): Gender? {
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		return queryFactory
			.select(matchUser.gender)
			.from(matchUser)
			.where(matchUser.userId.eq(requesterId))
			.fetchOne()
	}

	override fun search(requesterGender: Gender, requesterId: Long, nickname: String): List<InvitableUser> {
		val candidate: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val teamMember: QTeamMemberEntity = QTeamMemberEntity.teamMemberEntity

		return queryFactory
			.select(
				Projections.constructor(
					InvitableUser::class.java,
					candidate.userId,
					detail.nickname,
					detail.job,
					detail.companyName,
				),
			)
			.from(candidate)
			.join(detail).on(detail.userId.eq(candidate.userId))
			.where(
				detail.nickname.eq(nickname),
				candidate.gender.eq(requesterGender),
				candidate.userId.ne(requesterId),
				JPAExpressions.selectOne()
					.from(teamMember)
					.where(teamMember.userId.eq(candidate.userId))
					.notExists(),
			)
			.orderBy(candidate.userId.asc())
			.fetch()
	}
}
