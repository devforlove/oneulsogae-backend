package com.org.meeple.infra.user.mapper

import com.org.meeple.core.user.domain.UserCompany
import com.org.meeple.infra.user.entity.UserCompanyEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun UserCompanyEntity.toDomain(): UserCompany =
	UserCompany(
		id = id ?: 0,
		emailDomain = emailDomain,
		companyName = companyName,
	)
