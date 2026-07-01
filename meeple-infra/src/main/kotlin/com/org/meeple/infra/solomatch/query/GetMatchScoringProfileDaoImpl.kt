package com.org.meeple.infra.solomatch.query

import com.org.meeple.core.common.time.ageAt
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserIdealTypeEntity
import com.org.meeple.common.match.MatchScoringProfile
import com.org.meeple.scheduler.solomatch.query.dao.GetMatchScoringProfileDao
import com.querydsl.core.Tuple
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * [GetMatchScoringProfileDao]의 QueryDSL 구현. user_details를 기준으로 user_ideal_types를 left join해
 * 스코어링 프로필로 투영한다. (매칭 대상은 프로필을 갖춘 유저이므로 user_details 기준이 안전, 이상형은 선택적)
 * 나이는 birthday를 [today] 기준으로 계산해 담는다. soft delete 행은 각 엔티티의 @SQLRestriction으로 제외된다.
 * userId in (:userIds) 등치 IN은 user_details PK/유니크(user_id)로 받쳐진다.
 */
@Component
class GetMatchScoringProfileDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetMatchScoringProfileDao {

	override fun load(userIds: Set<Long>, today: LocalDate): Map<Long, MatchScoringProfile> {
		if (userIds.isEmpty()) return emptyMap()
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val ideal: QUserIdealTypeEntity = QUserIdealTypeEntity.userIdealTypeEntity
		val tuples: List<Tuple> = queryFactory
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
			.where(detail.userId.`in`(userIds))
			.fetch()
		return tuples.associate { tuple: Tuple ->
			val userId: Long = tuple.get(detail.userId)!!
			userId to MatchScoringProfile(
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
	}
}
