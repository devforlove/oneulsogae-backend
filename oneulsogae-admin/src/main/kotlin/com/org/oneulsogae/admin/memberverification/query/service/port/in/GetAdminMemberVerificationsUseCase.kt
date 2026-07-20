package com.org.oneulsogae.admin.memberverification.query.service.port.`in`

import com.org.oneulsogae.admin.memberverification.query.dto.AdminMemberVerificationDetailView
import com.org.oneulsogae.admin.memberverification.query.dto.AdminMemberVerificationPage
import com.org.oneulsogae.common.gathering.MemberVerificationStatus

/** 어드민 멤버 인증 조회 유스케이스. */
interface GetAdminMemberVerificationsUseCase {

	/** 멤버 인증을 최신순으로 [page](0부터)·[size] 단위 페이징 조회한다. [status] 생략 시 전체. */
	fun getVerifications(page: Int, size: Int, status: MemberVerificationStatus?): AdminMemberVerificationPage

	/** 멤버 인증 상세를 [id]로 조회한다. 없으면 예외를 던진다. */
	fun getVerification(id: Long): AdminMemberVerificationDetailView
}
