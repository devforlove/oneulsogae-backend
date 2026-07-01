package com.org.meeple.infra.solomatch.query

import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.time.ageAt
import com.org.meeple.core.solomatch.query.dao.GetExtraIntroCandidateDao
import com.org.meeple.core.solomatch.query.dto.ExtraIntroCandidate
import com.org.meeple.core.solomatch.query.dto.ExtraIntroScoringRow
import com.org.meeple.infra.matchuser.command.entity.QMatchUserEntity
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserIdealTypeEntity
import com.org.meeple.matching.MatchScoringProfile
import com.querydsl.core.Tuple
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [GetExtraIntroCandidateDao]의 QueryDSL 구현. 자격 후보는 match_user(mu)를 기준으로 반대 성별·최근 로그인·본인 제외로 거른 뒤
 * user_details(d)·user_ideal_types(i)를 명시 조인해 스코어링 프로필로 투영한다. (매칭 가능 = match_user 존재이므로 mu가 driving)
 * 나이는 birthday를 [today] 기준으로 계산한다. soft delete 행은 각 엔티티 @SQLRestriction으로 제외된다.
 * 재소개 제외는 solo_match_members self-join을 네이티브로 1회 조회해(소프트 삭제된 과거 소개까지 포함하도록 @SQLRestriction 우회) in-app 필터한다.
 */
@Component
class GetExtraIntroCandidateDaoImpl(
	private val queryFactory: JPAQueryFactory,
	private val entityManager: EntityManager,
) : GetExtraIntroCandidateDao {

	override fun findScoringRows(requesterId: Long, partnerGender: Gender, loginAfter: LocalDateTime, today: LocalDate): List<ExtraIntroScoringRow> {
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val ideal: QUserIdealTypeEntity = QUserIdealTypeEntity.userIdealTypeEntity

		val tuples: List<Tuple> = queryFactory
			.select(
				matchUser.userId,
				matchUser.regionId,
				matchUser.lastLoginAt,
				detail.birthday,
				detail.height,
				detail.maritalStatus,
				detail.smokingStatus,
				detail.drinkingStatus,
				detail.religion,
				ideal.ageMin,
				ideal.ageMax,
				ideal.heightMin,
				ideal.heightMax,
				ideal.maritalStatus,
				ideal.smokingStatus,
				ideal.drinkingStatus,
				ideal.religion,
			)
			.from(matchUser)
			.join(detail).on(detail.userId.eq(matchUser.userId))
			.leftJoin(ideal).on(ideal.userId.eq(matchUser.userId))
			.where(
				matchUser.gender.eq(partnerGender),
				matchUser.lastLoginAt.goe(loginAfter),
				matchUser.userId.ne(requesterId),
			)
			.fetch()

		val introducedUserIds: Set<Long> = findIntroducedPartnerIds(requesterId)
		return tuples.mapNotNull { tuple: Tuple ->
			val userId: Long = tuple.get(matchUser.userId)!!
			if (userId in introducedUserIds) return@mapNotNull null
			ExtraIntroScoringRow(
				userId = userId,
				regionId = tuple.get(matchUser.regionId)!!,
				lastLoginAt = tuple.get(matchUser.lastLoginAt)!!,
				profile = scoringProfile(tuple, userId, detail, ideal, today),
			)
		}
	}

	override fun findRequesterProfile(requesterId: Long, today: LocalDate): MatchScoringProfile? {
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val ideal: QUserIdealTypeEntity = QUserIdealTypeEntity.userIdealTypeEntity

		val tuple: Tuple = queryFactory
			.select(
				detail.userId,
				detail.birthday,
				detail.height,
				detail.maritalStatus,
				detail.smokingStatus,
				detail.drinkingStatus,
				detail.religion,
				ideal.ageMin,
				ideal.ageMax,
				ideal.heightMin,
				ideal.heightMax,
				ideal.maritalStatus,
				ideal.smokingStatus,
				ideal.drinkingStatus,
				ideal.religion,
			)
			.from(detail)
			.leftJoin(ideal).on(ideal.userId.eq(detail.userId))
			.where(detail.userId.eq(requesterId))
			.fetchOne() ?: return null
		return scoringProfile(tuple, requesterId, detail, ideal, today)
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

	private fun scoringProfile(
		tuple: Tuple,
		userId: Long,
		detail: QUserDetailEntity,
		ideal: QUserIdealTypeEntity,
		today: LocalDate,
	): MatchScoringProfile =
		MatchScoringProfile(
			userId = userId,
			age = tuple.get(detail.birthday)?.ageAt(today),
			height = tuple.get(detail.height),
			maritalStatus = tuple.get(detail.maritalStatus),
			smokingStatus = tuple.get(detail.smokingStatus),
			drinkingStatus = tuple.get(detail.drinkingStatus),
			religion = tuple.get(detail.religion),
			idealAgeMin = tuple.get(ideal.ageMin),
			idealAgeMax = tuple.get(ideal.ageMax),
			idealHeightMin = tuple.get(ideal.heightMin),
			idealHeightMax = tuple.get(ideal.heightMax),
			idealMaritalStatus = tuple.get(ideal.maritalStatus),
			idealSmokingStatus = tuple.get(ideal.smokingStatus),
			idealDrinkingStatus = tuple.get(ideal.drinkingStatus),
			idealReligion = tuple.get(ideal.religion),
		)
}
