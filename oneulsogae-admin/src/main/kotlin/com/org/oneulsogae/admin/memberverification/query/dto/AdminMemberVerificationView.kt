package com.org.oneulsogae.admin.memberverification.query.dto

import com.org.oneulsogae.common.gathering.MemberVerificationStatus
import java.time.LocalDateTime

/**
 * 어드민 멤버 인증 목록 한 건(read model).
 * 목록은 요약 정보만 담고, 사진 3종 열람 URL은 상세([AdminMemberVerificationDetailView])에서만 노출한다.
 */
data class AdminMemberVerificationView(
	val id: Long,
	val userId: Long,
	val nickname: String?,
	val email: String?,
	val status: MemberVerificationStatus,
	val jobCategory: String,
	val createdAt: LocalDateTime?,
)
