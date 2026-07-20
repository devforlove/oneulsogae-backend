package com.org.oneulsogae.admin.companyverification.query.service.port.`in`

import com.org.oneulsogae.admin.companyverification.query.dto.AdminCompanyVerificationDetailView
import com.org.oneulsogae.admin.companyverification.query.dto.AdminCompanyVerificationPage
import com.org.oneulsogae.common.user.CompanyImageVerificationStatus

/** 어드민 회사 이미지 인증 조회 유스케이스. */
interface GetAdminCompanyVerificationsUseCase {

	/** 회사 이미지 인증을 최신순으로 [page](0부터)·[size] 단위 페이징 조회한다. [status] 생략 시 전체. */
	fun getVerifications(page: Int, size: Int, status: CompanyImageVerificationStatus?): AdminCompanyVerificationPage

	/** 회사 이미지 인증 상세를 [id]로 조회한다. 없으면 예외를 던진다. */
	fun getVerification(id: Long): AdminCompanyVerificationDetailView
}
