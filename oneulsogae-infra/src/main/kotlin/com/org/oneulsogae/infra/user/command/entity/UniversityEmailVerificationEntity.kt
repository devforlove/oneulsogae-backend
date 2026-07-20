package com.org.oneulsogae.infra.user.command.entity

import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/**
 * 학교 이메일 인증(대학 인증) 요청을 추적하는 영속성 엔티티.
 * 발송한 1회용 인증번호(code)와 만료/검증 시각을 보관한다.
 * 도메인 로직은 UniversityEmailVerification 모델에 정의한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "university_email_verifications",
	indexes = [
		// user_id로 필터 후 PK(id) 내림차순으로 최신 1건을 찾는다. (InnoDB 보조 인덱스는 PK를 포함)
		Index(name = "idx_user_id", columnList = "user_id"),
	],
)
class UniversityEmailVerificationEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	@Column(name = "university_email", nullable = false)
	val universityEmail: String,

	@Column(name = "code", nullable = false, length = 16)
	val code: String,

	@Column(name = "expires_at", nullable = false)
	val expiresAt: LocalDateTime,

	@Column(name = "verified_at")
	var verifiedAt: LocalDateTime? = null,
) : BaseEntity()
