package com.org.meeple.infra.match.entity

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchType
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 남녀 1:1 매칭(소개) 영속성 엔티티.
 * (male_user_id, female_user_id) 유니크 제약으로 같은 쌍의 중복 소개를 차단한다. (재소개 방지)
 * introduced_date로 "하루에 한 번만 소개" 제약을 판단한다.
 * 복합 인덱스 (성별 컬럼, introduced_date)는 일별 소개 존재 확인 + 성별 컬럼 단독 조회를 모두 커버한다.
 * 도메인 로직은 [com.org.meeple.core.match.domain.Match] 모델에 정의한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "matches",
	uniqueConstraints = [
		UniqueConstraint(name = "udx_male_user_id_female_user_id", columnNames = ["male_user_id", "female_user_id"]),
	],
	indexes = [
		Index(name = "idx_male_user_id_introduced_date", columnList = "male_user_id, introduced_date"),
		Index(name = "idx_female_user_id_introduced_date", columnList = "female_user_id, introduced_date"),
	],
)
class MatchEntity(
	@Column(name = "male_user_id", nullable = false)
	val maleUserId: Long,

	@Column(name = "female_user_id", nullable = false)
	val femaleUserId: Long,

	@Column(name = "introduced_date", nullable = false)
	val introducedDate: LocalDate,

	/** 소개 만료 시각. 이 시각 이후로는 만료된 소개로 본다. */
	@Column(name = "expires_at", nullable = false)
	val expiresAt: LocalDateTime,

	@Column(name = "male_accepted")
	var maleAccepted: Boolean? = null,

	@Column(name = "female_accepted")
	var femaleAccepted: Boolean? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	var status: MatchStatus = MatchStatus.PROPOSED,

	/** 소개가 생성된 경로(일일 배치/온보딩/필수 신청). [com.org.meeple.common.match.MatchType] */
	@Enumerated(EnumType.STRING)
	@Column(name = "match_type", nullable = false, length = 20)
	val matchType: MatchType,

	/** 소개팅 신청에 드는 코인 비용. [com.org.meeple.common.coin.CoinUsageType.DATING_INIT]에서 가져온다. */
	@Column(name = "date_init_amount", nullable = false)
	val dateInitAmount: Int,

	/** 소개팅 수락에 드는 코인 비용. [com.org.meeple.common.coin.CoinUsageType.DATING_ACCEPT]에서 가져온다. */
	@Column(name = "date_accept_amount", nullable = false)
	val dateAcceptAmount: Int,
) : BaseEntity()
