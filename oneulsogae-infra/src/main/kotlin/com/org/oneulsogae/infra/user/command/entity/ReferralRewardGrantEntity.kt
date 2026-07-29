package com.org.oneulsogae.infra.user.command.entity

import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

/**
 * referral_reward_grants 테이블 영속성 엔티티. 추천 보상 1인 1회 지급을 강제하는 방어 기록이다.
 *
 * 다른 엔티티와 달리 `@SQLRestriction`(소프트 삭제 필터)을 두지 않는다 — 이 행이 조회에서 빠지면
 * 같은 사람이 추천 보상을 다시 받을 수 있어, 파기·탈퇴와 무관하게 항상 보여야 한다.
 * 원본 DI가 아니라 해시만 담아 개인정보를 보관하지 않는다.
 */
@Entity
@Table(
	name = "referral_reward_grants",
	indexes = [
		// 추천 실적 집계(referrer_user_id 동등 + count·sum)용.
		Index(name = "idx_referrer_user_id", columnList = "referrer_user_id"),
	],
	uniqueConstraints = [
		// 동시 요청까지 막는 최종 방어선. 판정은 존재 여부 조회로 하고, 이 제약은 안전망이다.
		UniqueConstraint(name = "ux_referred_di_hash", columnNames = ["referred_di_hash"]),
	],
)
class ReferralRewardGrantEntity(
	/** 피추천인 DI의 SHA-256 해시(hex 64자). */
	@Column(name = "referred_di_hash", nullable = false, length = 64)
	val referredDiHash: String,

	@Column(name = "referrer_user_id", nullable = false)
	val referrerUserId: Long,

	@Column(name = "referred_user_id", nullable = false)
	val referredUserId: Long,

	@Column(name = "coin_amount", nullable = false)
	val coinAmount: Int,

	@Column(name = "granted_at", nullable = false)
	val grantedAt: LocalDateTime,
) : BaseEntity()
