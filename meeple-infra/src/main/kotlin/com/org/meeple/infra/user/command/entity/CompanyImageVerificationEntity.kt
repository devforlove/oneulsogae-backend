package com.org.meeple.infra.user.command.entity

import com.org.meeple.common.user.CompanyImageVerificationStatus
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * 직장 서류 이미지 인증 제출을 추적하는 영속성 엔티티.
 * 업로드한 서류의 S3 오브젝트 키(image_key)와 심사 상태(status)를 보관한다. (파일 자체는 S3에 비공개 저장)
 * 도메인 로직은 [com.org.meeple.core.user.command.domain.CompanyImageVerification] 모델에 정의한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "company_image_verifications",
	indexes = [
		// user_id로 필터 후 PK(id) 내림차순으로 최신 제출을 찾는다. (심사 목록/최근 제출 조회 대비)
		Index(name = "idx_user_id", columnList = "user_id"),
	],
)
class CompanyImageVerificationEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	@Column(name = "image_key", nullable = false, length = 512)
	val imageKey: String,

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	var status: CompanyImageVerificationStatus = CompanyImageVerificationStatus.PENDING,

	@Column(name = "company_name", length = 100)
	var companyName: String? = null,

	@Column(name = "rejection_reason", length = 500)
	var rejectionReason: String? = null,
) : BaseEntity()
