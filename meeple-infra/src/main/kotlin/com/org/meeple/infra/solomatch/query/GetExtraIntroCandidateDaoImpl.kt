package com.org.meeple.infra.solomatch.query

import com.org.meeple.common.user.Gender
import com.org.meeple.core.solomatch.query.dao.GetExtraIntroCandidateDao
import com.org.meeple.core.solomatch.query.dto.ExtraIntroCandidate
import com.org.meeple.infra.matchuser.command.entity.QMatchUserEntity
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * [GetExtraIntroCandidateDao]의 QueryDSL 구현. 자격 후보는 match_user(mu) 단독으로 반대 성별·최근 로그인·본인 제외로 거른다.
 * (매칭 가능 = match_user 존재이므로 mu가 자격의 단일 기준) 목록은 마스킹 노출이라 스코어링 없이 id만 뽑고, 표시 프로필만 별도 조인한다.
 * 재소개 제외는 solo_match_members self-join을 네이티브로 1회 조회해(소프트 삭제된 과거 소개까지 포함하도록 @SQLRestriction 우회) in-app 필터한다.
 */
@Component
class GetExtraIntroCandidateDaoImpl(
	private val queryFactory: JPAQueryFactory,
	private val entityManager: EntityManager,
) : GetExtraIntroCandidateDao {

	override fun findEligibleCandidateIds(requesterId: Long, partnerGender: Gender, loginAfter: LocalDateTime): List<Long> {
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		val candidateIds: List<Long> = queryFactory
			.select(matchUser.userId)
			.from(matchUser)
			.where(
				matchUser.gender.eq(partnerGender),
				matchUser.lastLoginAt.goe(loginAfter),
				matchUser.userId.ne(requesterId),
			)
			.fetch()

		val introducedUserIds: Set<Long> = findIntroducedPartnerIds(requesterId)
		return candidateIds.filterNot { userId: Long -> userId in introducedUserIds }
	}

	override fun findDisplayProfiles(userIds: List<Long>): List<ExtraIntroCandidate> {
		if (userIds.isEmpty()) return emptyList()
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val region: QRegionEntity = QRegionEntity.regionEntity

		return queryFactory
			.select(
				Projections.constructor(
					ExtraIntroCandidate::class.java,
					detail.userId,
					detail.nickname,
					detail.profileImageCode,
					detail.birthday,
					detail.height,
					detail.gender,
					detail.job,
					// 표시용 활동지역은 regions를 join해 "시/도 시/군/구"로 만든다. (지역 미설정이면 null)
					region.sido.concat(" ").concat(region.sigungu),
					detail.introduction,
					detail.companyName,
					detail.universityName,
					// traits/interests는 @Convert(JSON) 컬럼이라 메타모델 ListPath로 select하면 컨버터가 안 먹어 기본 경로로 참조한다.
					Expressions.path(List::class.java, detail, "traits"),
					Expressions.path(List::class.java, detail, "interests"),
					detail.maritalStatus,
					detail.smokingStatus,
					detail.religion,
					detail.drinkingStatus,
					detail.bodyType,
				),
			)
			.from(detail)
			.leftJoin(region).on(region.id.eq(detail.regionId))
			.where(detail.userId.`in`(userIds))
			.fetch()
	}

	// 요청자와 같은 매칭에 함께 속했던 상대 userId 전체. (소프트 삭제된 과거 소개까지 포함해 영구 재소개 방지 — @SQLRestriction 우회 위해 네이티브)
	private fun findIntroducedPartnerIds(requesterId: Long): Set<Long> {
		val sql: String = """
			SELECT DISTINCT m2.user_id
			FROM solo_match_members m1
			JOIN solo_match_members m2 ON m2.match_id = m1.match_id AND m2.user_id <> m1.user_id
			WHERE m1.user_id = :requesterId
		""".trimIndent()
		val ids: List<*> = entityManager
			.createNativeQuery(sql)
			.setParameter("requesterId", requesterId)
			.resultList
		return ids.map { (it as Number).toLong() }.toSet()
	}
}
