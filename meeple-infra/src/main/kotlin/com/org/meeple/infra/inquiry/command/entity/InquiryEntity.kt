package com.org.meeple.infra.inquiry.command.entity

import com.org.meeple.common.inquiry.InquiryCategory
import com.org.meeple.common.inquiry.InquiryStatus
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/**
 * 고객센터 1:1 문의 영속성 엔티티. 작성 회원(user_id)·유형(category)·답변 이메일(email)·내용(message)을 보관한다.
 * 접수 시각은 별도 컬럼 없이 [BaseEntity]의 created_at(JPA Auditing)으로 갈음한다.
 * status/answer/answered_at은 추후 운영자 답변용으로 선반영했고, 생성 시 PENDING·null이다.
 * 삭제는 soft delete(deleted_at)로 처리한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "inquiries",
	indexes = [
		Index(name = "idx_user_id", columnList = "user_id"),
	],
)
class InquiryEntity(
	@Column(name = "user_id", nullable = false)
	var userId: Long,

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, columnDefinition = "varchar(50)")
	var category: InquiryCategory,

	@Column(name = "email", nullable = false, length = 255)
	var email: String,

	@Column(name = "message", nullable = false, length = 1000)
	var message: String,

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	var status: InquiryStatus = InquiryStatus.PENDING,

	@Column(name = "answer", length = 2000)
	var answer: String? = null,

	@Column(name = "answered_at")
	var answeredAt: LocalDateTime? = null,
) : BaseEntity()
