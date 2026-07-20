package com.org.oneulsogae.core.gathering.query.dto

import com.org.oneulsogae.common.gathering.MemberVerificationStatus

/**
 * 내 멤버 인증(본인인증) 제출 조회 결과(read model).
 * 화면 표시용이라 이미지 키는 내려주지 않고 식별자·심사 상태·직업 정보·반려 사유만 담는다.
 * 영속성은 [com.org.oneulsogae.infra.gathering.command.entity.MemberVerificationEntity]가 담당한다.
 */
data class MemberVerificationView(
	val id: Long,
	val status: MemberVerificationStatus,
	val jobCategory: String,
	val jobDetail: String,
	val rejectionReason: String?,
)
