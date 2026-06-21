package com.org.meeple.infra.match.query

import com.org.meeple.common.match.MatchMemberStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.query.dao.GetMatchWithPartnerDao
import com.org.meeple.core.match.query.dto.MatchWithPartner
import com.org.meeple.infra.match.command.entity.QSoloMatchEntity
import com.org.meeple.infra.match.command.entity.QSoloMatchMemberEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * [GetMatchWithPartnerDao]의 QueryDSL 구현체.
 * 매칭 헤더·내 참가자·상대 참가자·상대 프로필을 명시적 조인으로 한 번에 가져와(1+N 방지) 평탄 read model([MatchWithPartner])로 바로 투영한다. (조회 전용)
 * core 도메인/매퍼에 의존하지 않고 엔티티 필드에서 직접 구성한다.
 * - 관심 여부는 참가자 수락 플래그(accepted, nullable)를 `coalesce(false)`로 정리해 산출한다. (null=미응답=관심없음)
 * - traits/interests는 `@Convert`(JSON) 컬럼이라 QueryDSL 메타모델이 `ListPath`(컬렉션)로 만들어 그대로 select하면 컨버터가 적용되지 않으므로, [Expressions.path]로 기본 속성 경로로 참조한다.
 * 단건/존재 조회·저장 out-port는 [com.org.meeple.infra.match.command.adapter.MatchAdapter]가 메서드 쿼리로 따로 구현한다.
 */
@Component
class GetMatchWithPartnerDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetMatchWithPartnerDao {

	/**
	 * 사용자가 참가한 매칭 + 상대 프로필을 조인 조회한다. (만료된 소개는 now 기준으로 제외)
	 * 내 참가자 행(solo_match_members.user_id = :userId, → idx_user_id)에서 출발해 매칭 헤더·상대 참가자·상대 프로필을 명시적 조인으로 가져온다.
	 * 1:1이라 상대 참가자는 정확히 한 명이다. ([gender]는 컬럼 선택에 쓰지 않아 무시한다)
	 */
	override fun findAllWithPartnerByUserId(userId: Long, gender: Gender, now: LocalDateTime): List<MatchWithPartner> {
		val match: QSoloMatchEntity = QSoloMatchEntity.soloMatchEntity
		val myMember: QSoloMatchMemberEntity = QSoloMatchMemberEntity.soloMatchMemberEntity
		val partnerMember: QSoloMatchMemberEntity = QSoloMatchMemberEntity("partnerMember")
		val partnerDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity

		return queryFactory
			.select(
				Projections.constructor(
					MatchWithPartner::class.java,
					match.id,
					match.status,
					match.expiresAt,
					match.dateInitAmount,
					match.dateAcceptAmount,
					myMember.accepted.coalesce(false),
					partnerMember.accepted.coalesce(false),
					partnerDetail.userId,
					partnerDetail.nickname,
					partnerDetail.profileImageCode,
					partnerDetail.birthday,
					partnerDetail.height,
					partnerDetail.gender,
					partnerDetail.job,
					partnerDetail.activityArea,
					partnerDetail.introduction,
					partnerDetail.companyName,
					Expressions.path(List::class.java, partnerDetail, "traits"),
					Expressions.path(List::class.java, partnerDetail, "interests"),
					partnerDetail.maritalStatus,
					partnerDetail.smokingStatus,
					partnerDetail.religion,
					partnerDetail.drinkingStatus,
					partnerDetail.bodyType,
				),
			)
			.from(myMember)
			.join(match).on(match.id.eq(myMember.matchId))
			.join(partnerMember).on(
				partnerMember.matchId.eq(match.id),
				partnerMember.userId.ne(myMember.userId),
				// 상대 status는 거르지 않는다. 상대가 나갔어도(DEACTIVE) 내 목록엔 남겨 상대 프로필과 함께 보여준다.
			)
			.join(partnerDetail).on(partnerDetail.userId.eq(partnerMember.userId))
			.where(
				myMember.userId.eq(userId),
				// 내가 나간(DEACTIVE) 매칭은 내 목록에서 제외한다. (나는 활성 참가자만)
				myMember.status.eq(MatchMemberStatus.ACTIVE),
				match.expiresAt.goe(now),
			)
			.fetch()
	}
}
