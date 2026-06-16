package com.org.meeple.infra.user.query

import com.org.meeple.core.user.query.dao.UserCompanyQueryDao
import com.org.meeple.core.user.query.dto.UserCompany
import com.org.meeple.infra.user.command.entity.QUserCompanyEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [UserCompanyQueryDao]의 QueryDSL 구현체. (조회 전용 — 쓰기 경로가 없는 lookup)
 */
@Component
class UserCompanyQueryDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : UserCompanyQueryDao {

	override fun findByEmailDomain(emailDomain: String): UserCompany? {
		val company: QUserCompanyEntity = QUserCompanyEntity.userCompanyEntity
		return queryFactory
			.select(
				Projections.constructor(
					UserCompany::class.java,
					company.id,
					company.emailDomain,
					company.companyName,
				),
			)
			.from(company)
			.where(company.emailDomain.eq(emailDomain))
			.fetchOne()
	}
}
