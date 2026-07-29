package com.org.oneulsogae.infra.user.query

import com.org.oneulsogae.core.user.query.dao.GetUserCompanyDao
import com.org.oneulsogae.core.user.query.dto.UserCompany
import com.org.oneulsogae.infra.user.command.entity.QUserCompanyEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetUserCompanyDao]의 QueryDSL 구현체. (조회 전용 — 쓰기 경로가 없는 lookup)
 * 도메인 조회는 `ux_email_domain_company_name` 유니크 키의 선두 컬럼(email_domain)을 seek로 탄다.
 */
@Component
class GetUserCompanyDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetUserCompanyDao {

	override fun findAllByEmailDomain(emailDomain: String): List<UserCompany> {
		val company: QUserCompanyEntity = QUserCompanyEntity.userCompanyEntity
		return queryFactory
			.select(userCompanyProjection(company))
			.from(company)
			.where(company.emailDomain.eq(emailDomain))
			.orderBy(company.companyName.asc())
			.fetch()
	}

	override fun findById(userCompanyId: Long): UserCompany? {
		val company: QUserCompanyEntity = QUserCompanyEntity.userCompanyEntity
		return queryFactory
			.select(userCompanyProjection(company))
			.from(company)
			.where(company.id.eq(userCompanyId))
			.fetchOne()
	}

	private fun userCompanyProjection(company: QUserCompanyEntity) =
		Projections.constructor(
			UserCompany::class.java,
			company.id,
			company.emailDomain,
			company.companyName,
		)
}
