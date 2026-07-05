package com.org.meeple.admin.companyverification.query.dao

import com.org.meeple.admin.companyverification.query.dto.AdminCompanyVerificationViews
import com.org.meeple.common.user.CompanyImageVerificationStatus

/** 어드민 회사 이미지 인증 조회 dao(query out-port). */
interface GetAdminCompanyVerificationDao {

	/** [status](없으면 전체)를 최신순(id desc)으로 [offset]부터 [limit]건 조회한다. */
	fun findPage(offset: Long, limit: Int, status: CompanyImageVerificationStatus?): AdminCompanyVerificationViews

	/** (soft delete 제외) [status](없으면 전체) 조건 전체 개수. (페이징 메타데이터 계산용) */
	fun count(status: CompanyImageVerificationStatus?): Long
}
