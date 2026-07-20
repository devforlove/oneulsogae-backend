package com.org.oneulsogae.infra.solomatch.query

import com.org.oneulsogae.common.match.MatchMemberStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.solomatch.query.dao.GetMatchWithPartnerDao
import com.org.oneulsogae.core.solomatch.query.dto.MatchWithPartner
import com.org.oneulsogae.infra.solomatch.command.entity.QSoloMatchEntity
import com.org.oneulsogae.infra.solomatch.command.entity.QSoloMatchMemberEntity
import com.org.oneulsogae.infra.region.entity.QRegionEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * [GetMatchWithPartnerDao]의 QueryDSL 구현체.
 * 매칭 헤더·내 참가자·상대 참가자·상대 프로필을 명시적 조인으로 한 번에 가져와(1+N 방지) 평탄 read model([MatchWithPartner])로 바로 투영한다. (조회 전용)
 * core 도메인/매퍼에 의존하지 않고 엔티티 필드에서 직접 구성한다.
 * - 관심 여부는 참가자 status가 APPLY/ACTIVE인지로 산출한다.
 * - traits/interests는 `@Convert`(JSON) 컬럼이라 QueryDSL 메타모델이 `ListPath`(컬렉션)로 만들어 그대로 select하면 컨버터가 적용되지 않으므로, [Expressions.path]로 기본 속성 경로로 참조한다.
 * 단건/존재 조회·저장 out-port는 [com.org.oneulsogae.infra.solomatch.command.adapter.MatchAdapter]가 메서드 쿼리로 따로 구현한다.
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
		val soloMatch: QSoloMatchEntity = QSoloMatchEntity.soloMatchEntity
		val mySoloMatchMember: QSoloMatchMemberEntity = QSoloMatchMemberEntity("mySoloMatchMember")
		val partnerSoloMatchMember: QSoloMatchMemberEntity = QSoloMatchMemberEntity("partnerSoloMatchMember")
		val userDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val partnerUser: QUserEntity = QUserEntity.userEntity
		val region: QRegionEntity = QRegionEntity.regionEntity

		return queryFactory
			.select(
				Projections.constructor(
					MatchWithPartner::class.java,
					soloMatch.id,
					soloMatch.status,
					soloMatch.expiresAt,
					soloMatch.dateInitAmount,
					soloMatch.dateAcceptAmount,
					mySoloMatchMember.status.`in`(MatchMemberStatus.APPLY, MatchMemberStatus.ACTIVE),
					partnerSoloMatchMember.status.`in`(MatchMemberStatus.APPLY, MatchMemberStatus.ACTIVE),
					mySoloMatchMember.checkedAt,
					userDetail.userId,
					userDetail.nickname,
					userDetail.profileImageCode,
					userDetail.birthday,
					userDetail.height,
					userDetail.gender,
					userDetail.job,
					// 표시용 활동지역은 regions를 join해 "시/도 시/군/구"로 만든다. (지역 미설정이면 null)
					region.sido.concat(" ").concat(region.sigungu),
					userDetail.introduction,
					userDetail.companyName,
					userDetail.universityName,
					Expressions.path(List::class.java, userDetail, "traits"),
					Expressions.path(List::class.java, userDetail, "interests"),
					userDetail.maritalStatus,
					userDetail.smokingStatus,
					userDetail.religion,
					userDetail.drinkingStatus,
					userDetail.bodyType,
					partnerUser.lastLoginAt,
				),
			)
			.from(mySoloMatchMember)
			.join(soloMatch).on(soloMatch.id.eq(mySoloMatchMember.matchId))
			.join(partnerSoloMatchMember).on(
				partnerSoloMatchMember.matchId.eq(soloMatch.id),
				partnerSoloMatchMember.userId.ne(mySoloMatchMember.userId),
				// 상대 status는 거르지 않는다. 상대가 나갔어도(DEACTIVE) 내 목록엔 남겨 상대 프로필과 함께 보여준다.
			)
			.join(userDetail).on(userDetail.userId.eq(partnerSoloMatchMember.userId))
			// lastLoginAt 원천인 users를 조인한다. 상대 users 행이 없어도 매칭은 목록에 남기므로 leftJoin.
			.leftJoin(partnerUser).on(partnerUser.id.eq(partnerSoloMatchMember.userId))
			.leftJoin(region).on(region.id.eq(userDetail.regionId))
			.where(
				mySoloMatchMember.userId.eq(userId),
				// 내가 나간(DEACTIVE) 매칭만 내 목록에서 제외한다. (대기·신청·활성 매칭은 노출)
				mySoloMatchMember.status.ne(MatchMemberStatus.DEACTIVE),
				soloMatch.expiresAt.goe(now),
			)
			.fetch()
	}
}
