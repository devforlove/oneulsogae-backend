package com.org.oneulsogae.admin.memberverification.query.dto

import com.org.oneulsogae.common.gathering.MemberVerificationStatus
import java.time.LocalDateTime

/**
 * 어드민 멤버 인증 상세 read model. 목록 필드 + 직업 상세([jobDetail])·반려 사유([rejectionReason]) +
 * 사진 3종(얼굴·신분증·서류)의 오브젝트 키.
 * dao는 이미지 키까지 채우고 열람 URL 3종은 null로 둔다. 서비스가 presign 결과로 채운다.
 * (QueryDSL Projections.constructor가 URL 없이 투영할 수 있도록 12-arg 보조 생성자를 둔다)
 */
data class AdminMemberVerificationDetailView(
	val id: Long,
	val userId: Long,
	val nickname: String?,
	val email: String?,
	val status: MemberVerificationStatus,
	val jobCategory: String,
	val jobDetail: String,
	val rejectionReason: String?,
	val createdAt: LocalDateTime?,
	val faceImageKey: String,
	val idCardImageKey: String,
	val documentImageKey: String,
	val faceImageUrl: String? = null,
	val idCardImageUrl: String? = null,
	val documentImageUrl: String? = null,
) {
	/** dao 투영용 생성자. 열람 URL 3종은 서비스가 presign으로 채운다. */
	constructor(
		id: Long,
		userId: Long,
		nickname: String?,
		email: String?,
		status: MemberVerificationStatus,
		jobCategory: String,
		jobDetail: String,
		rejectionReason: String?,
		createdAt: LocalDateTime?,
		faceImageKey: String,
		idCardImageKey: String,
		documentImageKey: String,
	) : this(
		id, userId, nickname, email, status, jobCategory, jobDetail, rejectionReason, createdAt,
		faceImageKey, idCardImageKey, documentImageKey, null, null, null,
	)
}
