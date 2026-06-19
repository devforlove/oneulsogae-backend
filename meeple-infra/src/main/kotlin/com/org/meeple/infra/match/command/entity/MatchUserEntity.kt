package com.org.meeple.infra.match.command.entity

import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

/**
 * 매칭 읽기 모델(match_user) 영속성 엔티티. match 도메인이 소유하며, user 도메인 이벤트로부터 동기화된다.
 * 매칭에 필요한 기준 필드만 담고, 행의 존재 자체가 "매칭 가능(정식 가입 + 필수 필드 완성)"을 의미한다.
 * (그래서 후보 조회는 status·null 검사·user_details 조인 없이 이 테이블 단독으로 수행한다)
 * user_id를 PK로 두어 upsert/삭제 키로 쓴다. 소프트 삭제 대신 매칭 불가 전이 시 행을 하드 삭제한다.
 */
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
	name = "match_user",
	indexes = [
		// 온보딩 추천 후보 선정(반대 성별·같은 권역·최근 로그인)용
		Index(name = "idx_match_user_candidate", columnList = "gender, region_code, last_login_at"),
		// 일일 배치 대상 (lastLoginAt, userId) 키셋 스캔용
		Index(name = "idx_match_user_recent_login", columnList = "last_login_at, user_id"),
	],
)
class MatchUserEntity(
	@Id
	@Column(name = "user_id")
	val userId: Long,

	@Enumerated(EnumType.STRING)
	@Column(name = "gender", nullable = false)
	var gender: Gender,

	@Column(name = "region_code", nullable = false)
	var regionCode: Int,

	@Enumerated(EnumType.STRING)
	@Column(name = "marital_status", nullable = false)
	var maritalStatus: MaritalStatus,

	@Column(name = "age", nullable = false)
	var age: Int,

	@Column(name = "nickname", nullable = false)
	var nickname: String,

	@Column(name = "last_login_at", nullable = false)
	var lastLoginAt: LocalDateTime,
) {

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	var createdAt: LocalDateTime? = null
		protected set

	@LastModifiedDate
	@Column(name = "updated_at")
	var updatedAt: LocalDateTime? = null
		protected set
}
