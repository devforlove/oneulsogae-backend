package com.org.oneulsogae.core.user.query.service

import com.org.oneulsogae.core.user.query.dao.GetUserCompanyDao
import com.org.oneulsogae.core.user.query.dto.UserCompany
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserCompanyUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetUserCompanyUseCase] 구현.
 * 회사 이메일의 도메인으로 회사 후보를 조회한다. (도메인은 대소문자 구분 없이 매칭)
 */
@Service
@Transactional(readOnly = true)
class GetUserCompanyService(
	private val getUserCompanyDao: GetUserCompanyDao,
) : GetUserCompanyUseCase {

	override fun findCompaniesByEmail(companyEmail: String): List<UserCompany> {
		val domain: String = companyEmail.substringAfter('@', "").lowercase()
		if (domain.isBlank()) return emptyList()
		return getUserCompanyDao.findAllByEmailDomain(domain)
	}

	override fun findById(userCompanyId: Long): UserCompany? =
		getUserCompanyDao.findById(userCompanyId)
}
