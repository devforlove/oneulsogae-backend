package com.org.oneulsogae.infra.user.query

import com.org.oneulsogae.core.user.query.dao.GetUserUniversityDao
import com.org.oneulsogae.core.user.query.dto.UserUniversity
import com.org.oneulsogae.infra.user.command.entity.QUserUniversityEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetUserUniversityDao]의 QueryDSL 구현체. (조회 전용 — 쓰기 경로가 없는 lookup)
 */
@Component
class GetUserUniversityDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetUserUniversityDao {

	override fun findByEmailDomain(emailDomain: String): UserUniversity? {
		val university: QUserUniversityEntity = QUserUniversityEntity.userUniversityEntity
		return queryFactory
			.select(
				Projections.constructor(
					UserUniversity::class.java,
					university.id,
					university.emailDomain,
					university.universityName,
				),
			)
			.from(university)
			.where(university.emailDomain.eq(emailDomain))
			.fetchOne()
	}
}
