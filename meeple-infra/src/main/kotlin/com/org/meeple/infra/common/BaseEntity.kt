package com.org.meeple.infra.common

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

/**
 * 모든 엔티티의 공통 베이스. 식별자(id)와 생성/수정/삭제 시각을 제공한다.
 * 새 엔티티는 이 클래스를 상속하면 동일한 PK 전략과 감사(auditing) 컬럼을 그대로 갖는다.
 * - created_at, updated_at: JPA Auditing이 자동으로 채운다.
 * - deleted_at: 소프트 삭제 시각. null이면 미삭제 상태.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	var id: Long? = null

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	var createdAt: LocalDateTime? = null
		protected set

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	var updatedAt: LocalDateTime? = null
		protected set

	@Column(name = "deleted_at")
	var deletedAt: LocalDateTime? = null
		protected set

	val isNew: Boolean
		get() = id == null

	val isDeleted: Boolean
		get() = deletedAt != null

	/** 소프트 삭제 처리한다. */
	fun softDelete(at: LocalDateTime = LocalDateTime.now()) {
		this.deletedAt = at
	}

	/** 소프트 삭제를 취소한다. */
	fun restore() {
		this.deletedAt = null
	}
}
