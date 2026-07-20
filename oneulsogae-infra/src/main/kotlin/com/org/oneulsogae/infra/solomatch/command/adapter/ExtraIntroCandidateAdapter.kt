package com.org.oneulsogae.infra.solomatch.command.adapter

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.time.ageAt
import com.org.oneulsogae.core.solomatch.command.application.port.out.ExtraIntroCandidateRow
import com.org.oneulsogae.core.solomatch.command.application.port.out.GetExtraIntroCandidatePort
import com.org.oneulsogae.core.solomatch.command.domain.MatchMembers
import com.org.oneulsogae.infra.matchuser.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserIdealTypeEntity
import com.org.oneulsogae.common.match.selection.MatchScoringProfile
import com.querydsl.core.Tuple
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [GetExtraIntroCandidatePort]의 command 어댑터. 자격 후보(반대 성별·최근 로그인·본인 제외 · match_user 존재)를
 * user_details·user_ideal_types 명시 조인으로 [ExtraIntroCandidateRow]로 투영한다.
 * 재소개 제외는 여기서 하지 않고 선택 단계에서 [existsIntroduced]로 판정한다.
 * CQRS상 조회 dao(GetExtraIntroCandidateDaoImpl)와 같은 조회라도 공유하지 않고 command가 자체 구현한다.
 */
@Component
class ExtraIntroCandidateAdapter(
	private val queryFactory: JPAQueryFactory,
	private val entityManager: EntityManager,
) : GetExtraIntroCandidatePort {

	override fun findCandidates(requesterId: Long, partnerGender: Gender, loginAfter: LocalDateTime, today: LocalDate): List<ExtraIntroCandidateRow> {
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val ideal: QUserIdealTypeEntity = QUserIdealTypeEntity.userIdealTypeEntity

		val tuples: List<Tuple> = queryFactory
			.select(
				matchUser.userId,
				matchUser.regionId,
				matchUser.lastLoginAt,
				matchUser.companyName,
				matchUser.refuseSameCompanyIntro,
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

		return tuples.map { tuple: Tuple ->
			val userId: Long = tuple.get(matchUser.userId)!!
			ExtraIntroCandidateRow(
				userId = userId,
				regionId = tuple.get(matchUser.regionId)!!,
				lastLoginAt = tuple.get(matchUser.lastLoginAt)!!,
				companyName = tuple.get(matchUser.companyName),
				refuseSameCompanyIntro = tuple.get(matchUser.refuseSameCompanyIntro)!!,
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

	// 참가자 조합 키(정렬된 userId)로 소개 이력 존재 여부를 확인한다. (ux_member_key)
	// 소프트 삭제된 과거 소개도 member_key를 그대로 점유하므로(ux_member_key는 deleted_at 미포함) 삭제 행까지 봐야 한다 → @SQLRestriction 우회 위해 네이티브.
	override fun existsIntroduced(requesterId: Long, candidateId: Long): Boolean {
		val sql: String = """
			SELECT 1
			FROM solo_matches
			WHERE member_key = :memberKey
			LIMIT 1
		""".trimIndent()
		return entityManager
			.createNativeQuery(sql)
			.setParameter("memberKey", MatchMembers.memberKeyOf(listOf(requesterId, candidateId)))
			.resultList
			.isNotEmpty()
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
