package com.org.oneulsogae.infra.user.command.entity

import com.org.oneulsogae.common.user.UniversityImageVerificationStatus
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * 학교 서류 이미지 인증 제출을 추적하는 영속성 엔티티. (직장 서류 인증 [CompanyImageVerificationEntity]와 같은 구성)
 * 업로드한 서류의 S3 오브젝트 키(image_key)와 심사 상태(status)를 보관한다. (파일 자체는 S3에 비공개 저장)
 * 도메인 로직은 [com.org.oneulsogae.core.user.command.domain.UniversityImageVerification] 모델에 정의한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "university_image_verifications",
	indexes = [
		// user_id로 필터 후 PK(id) 내림차순으로 최신 제출을 찾는다. (심사 목록/최근 제출 조회 대비)
		Index(name = "idx_user_id", columnList = "user_id"),
	],
)
class UniversityImageVerificationEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	@Column(name = "image_key", nullable = false, length = 512)
	val imageKey: String,

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	var status: UniversityImageVerificationStatus = UniversityImageVerificationStatus.PENDING,

	@Column(name = "university_name", length = 100)
	var universityName: String? = null,

	/** 제출 시점의 유저 프로필 학교명 스냅샷. 승인으로 프로필이 덮어써져도 심사 상세에서 이전 학교명을 안정적으로 보여준다. */
	@Column(name = "previous_university_name", length = 100)
	var previousUniversityName: String? = null,

	@Column(name = "rejection_reason", length = 500)
	var rejectionReason: String? = null,
) : BaseEntity()
