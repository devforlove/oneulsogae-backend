package com.org.meeple.infra.match.adapter

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.application.port.out.GetMatchWithPartnerPort
import com.org.meeple.core.match.domain.Match
import com.org.meeple.core.match.domain.MatchMembers
import com.org.meeple.core.match.domain.MatchWithPartner
import com.org.meeple.core.user.domain.UserDetail
import com.org.meeple.infra.match.entity.QMatchEntity
import com.org.meeple.infra.match.entity.QMatchMemberEntity
import com.org.meeple.infra.match.mapper.toDomain
import com.org.meeple.infra.user.entity.QUserDetailEntity
import com.org.meeple.infra.user.mapper.toDomain as toUserDetailDomain
import com.querydsl.core.Tuple
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * core 모듈이 쓰는 [MatchEntity]의 QueryDSL 어댑터.
 * 상대 프로필 조인 조회([GetMatchWithPartnerPort])만 전담하며, `JPAQueryFactory`만 주입한다.
 * 단건/존재 조회·저장은 [MatchCoreAdapter]가 별도로 둔다.
 */
@Component
class MatchQueryCoreAdapter(
	private val queryFactory: JPAQueryFactory,
) : GetMatchWithPartnerPort {

	/**
	 * 사용자가 참가한 매칭 + 상대 프로필을 조인 조회한다. (만료된 소개는 now 기준으로 제외)
	 * 내 참가자 행(match_members.user_id = :userId, → idx_user_id)에서 출발해 매칭 헤더·상대 참가자·상대 프로필을 명시적 조인으로 한 번에 가져온다. (1+N 방지)
	 * 1:1이라 상대 참가자는 정확히 한 명이다. (성별 파라미터는 더 이상 컬럼 선택에 쓰지 않아 무시한다)
	 */
	override fun findAllWithPartnerByUserId(userId: Long, gender: Gender, now: LocalDateTime): List<MatchWithPartner> {
		val match: QMatchEntity = QMatchEntity.matchEntity
		val myMember: QMatchMemberEntity = QMatchMemberEntity.matchMemberEntity
		val partnerMember: QMatchMemberEntity = QMatchMemberEntity("partnerMember")
		val partnerDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity

		val rows: List<Tuple> = queryFactory
			.select(match, myMember, partnerMember, partnerDetail)
			.from(myMember)
			.join(match).on(match.id.eq(myMember.matchId))
			.join(partnerMember).on(
				partnerMember.matchId.eq(match.id),
				partnerMember.userId.ne(myMember.userId),
			)
			.join(partnerDetail).on(partnerDetail.userId.eq(partnerMember.userId))
			.where(
				myMember.userId.eq(userId),
				match.expiresAt.goe(now),
			)
			.fetch()

		return rows.map { row: Tuple ->
			val members: MatchMembers = MatchMembers(
				listOf(row.get(myMember)!!.toDomain(), row.get(partnerMember)!!.toDomain()),
			)
			val matchDomain: Match = row.get(match)!!.toDomain(members)
			val partner: UserDetail = row.get(partnerDetail)!!.toUserDetailDomain()
			MatchWithPartner(userId, matchDomain, partner)
		}
	}
}
