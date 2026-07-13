package com.org.meeple.infra.user.command.entity

import com.org.meeple.common.user.MemberVerificationStatus
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * 멤버 인증(본인인증) 제출을 추적하는 영속성 엔티티.
 * 직업 정보(직종·직장명/직종/직급)와 사진 3종(얼굴·전신·서류)의 S3 오브젝트 키, 심사 상태(status)를 보관한다.
 * (파일 자체는 S3에 비공개 저장) 도메인 로직은 [com.org.meeple.core.user.command.domain.MemberVerification] 모델에 정의한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "member_verifications",
	indexes = [
		// user_id로 필터 후 PK(id) 내림차순으로 최신 제출을 찾는다. (심사 목록/최근 제출 조회 대비)
		Index(name = "idx_user_id", columnList = "user_id"),
	],
)
class MemberVerificationEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	@Column(name = "job_category", nullable = false, length = 30)
	val jobCategory: String,

	@Column(name = "job_detail", nullable = false, length = 100)
	val jobDetail: String,

	@Column(name = "face_image_key", nullable = false, length = 512)
	val faceImageKey: String,

	@Column(name = "body_image_key", nullable = false, length = 512)
	val bodyImageKey: String,

	@Column(name = "document_image_key", nullable = false, length = 512)
	val documentImageKey: String,

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	var status: MemberVerificationStatus = MemberVerificationStatus.PENDING,

	@Column(name = "rejection_reason", length = 500)
	var rejectionReason: String? = null,
) : BaseEntity()
