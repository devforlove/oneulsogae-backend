package com.org.oneulsogae.core.user.query.service

import com.org.oneulsogae.core.user.query.dao.GetUserUniversityDao
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserUniversityUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetUserUniversityUseCase] 구현.
 * 학교 이메일의 도메인으로 학교명을 조회한다. (도메인은 대소문자 구분 없이 매칭)
 */
@Service
class GetUserUniversityService(
	private val getUserUniversityDao: GetUserUniversityDao,
) : GetUserUniversityUseCase {

	@Transactional(readOnly = true)
	override fun findUniversityNameByEmail(universityEmail: String): String? {
		val domain: String = universityEmail.substringAfter('@', "").lowercase()
		if (domain.isBlank()) return null
		return getUserUniversityDao.findByEmailDomain(domain)?.universityName
	}
}
