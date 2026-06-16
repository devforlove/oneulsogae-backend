package com.org.meeple.infra.match.query

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.query.dao.MatchWithPartnerQueryDao
import com.org.meeple.core.match.query.dto.MatchWithPartner
import com.org.meeple.infra.match.command.entity.MatchEntity
import com.org.meeple.infra.match.command.entity.MatchMemberEntity
import com.org.meeple.infra.match.command.entity.QMatchEntity
import com.org.meeple.infra.match.command.entity.QMatchMemberEntity
import com.org.meeple.infra.user.entity.QUserDetailEntity
import com.org.meeple.infra.user.entity.UserDetailEntity
import com.querydsl.core.Tuple
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * [MatchWithPartnerQueryDao]의 QueryDSL 구현체.
 * 매칭 헤더·내 참가자·상대 참가자·상대 프로필을 명시적 조인으로 한 번에 가져와(1+N 방지) 평탄 read model로 투영한다. (조회 전용)
 * core 도메인/매퍼에 의존하지 않고 엔티티 필드에서 [MatchWithPartner]를 직접 구성한다.
 * (관심 여부는 참가자 수락 플래그 myMember/partnerMember.accepted로 산출)
 * 단건/존재 조회·저장 out-port는 [com.org.meeple.infra.match.command.adapter.MatchCoreAdapter]가 메서드 쿼리로 따로 구현한다.
 */
@Component
class MatchWithPartnerQueryDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : MatchWithPartnerQueryDao {

	/**
	 * 사용자가 참가한 매칭 + 상대 프로필을 조인 조회한다. (만료된 소개는 now 기준으로 제외)
	 * 내 참가자 행(match_members.user_id = :userId, → idx_user_id)에서 출발해 매칭 헤더·상대 참가자·상대 프로필을 명시적 조인으로 가져온다.
	 * 1:1이라 상대 참가자는 정확히 한 명이다. ([gender]는 컬럼 선택에 쓰지 않아 무시한다)
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
			val matchEntity: MatchEntity = row.get(match)!!
			val myMemberEntity: MatchMemberEntity = row.get(myMember)!!
			val partnerMemberEntity: MatchMemberEntity = row.get(partnerMember)!!
			val partnerDetailEntity: UserDetailEntity = row.get(partnerDetail)!!
			MatchWithPartner(
				matchId = matchEntity.id!!,
				status = matchEntity.status,
				expiresAt = matchEntity.expiresAt,
				datingInitAmount = matchEntity.dateInitAmount,
				datingAcceptAmount = matchEntity.dateAcceptAmount,
				hasUserInterest = myMemberEntity.accepted == true,
				hasPartnerInterest = partnerMemberEntity.accepted == true,
				partnerUserId = partnerDetailEntity.userId,
				nickname = partnerDetailEntity.nickname,
				profileImageCode = partnerDetailEntity.profileImageCode,
				age = partnerDetailEntity.age,
				height = partnerDetailEntity.height,
				gender = partnerDetailEntity.gender,
				job = partnerDetailEntity.job,
				activityArea = partnerDetailEntity.activityArea,
				introduction = partnerDetailEntity.introduction,
				companyName = partnerDetailEntity.companyName,
				traits = partnerDetailEntity.traits,
				interests = partnerDetailEntity.interests,
				maritalStatus = partnerDetailEntity.maritalStatus,
				smokingStatus = partnerDetailEntity.smokingStatus,
				religion = partnerDetailEntity.religion,
				drinkingStatus = partnerDetailEntity.drinkingStatus,
				bodyType = partnerDetailEntity.bodyType,
			)
		}
	}
}
