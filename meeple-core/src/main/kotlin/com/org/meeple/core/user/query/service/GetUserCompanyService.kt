package com.org.meeple.core.user.query.service

import com.org.meeple.core.user.query.dao.GetUserCompanyDao
import com.org.meeple.core.user.query.service.port.`in`.GetUserCompanyUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetUserCompanyUseCase] 구현.
 * 회사 이메일의 도메인으로 회사명을 조회한다. (도메인은 대소문자 구분 없이 매칭)
 */
@Service
class GetUserCompanyService(
	private val getUserCompanyDao: GetUserCompanyDao,
) : GetUserCompanyUseCase {

	@Transactional(readOnly = true)
	override fun findCompanyNameByEmail(companyEmail: String): String? {
		val domain: String = companyEmail.substringAfter('@', "").lowercase()
		if (domain.isBlank()) return null
		return getUserCompanyDao.findByEmailDomain(domain)?.companyName
	}
}
