package com.org.meeple.infra.match.command.entity

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.SoloMatchType
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
 * 1:1(솔로) 매칭(소개)의 헤더 영속성 엔티티. 참가자는 [SoloMatchMemberEntity]가 정규화해 보관한다. (2:2 팀 매칭은 [TeamMatchEntity]가 담당)
 * member_key(참가자 조합의 정규화 키)에 유니크 제약을 걸어 같은 조합의 중복 소개를 차단한다. (재소개 방지)
 * introduced_date로 "하루에 한 번만 소개" 제약을 판단한다. (사용자별 일일 소개 존재 확인은 solo_match_members와 조인해 본다)
 * 도메인 로직은 [com.org.meeple.core.match.command.domain.Match] 모델에 정의한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "solo_matches",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_member_key", columnNames = ["member_key"]),
	],
	indexes = [
		// 오늘 소개된 유저 제외(introduced_date = today) seek용
		Index(name = "idx_introduced_date", columnList = "introduced_date"),
		// 성사(status = MATCHED) 유저 제외 seek용
		Index(name = "idx_status", columnList = "status"),
		// 만료 정리 배치: status 등치 + expires_at 범위 조회를 받친다.
		Index(name = "idx_status_expires_at", columnList = "status, expires_at"),
	],
)
class SoloMatchEntity(
	/** 참가자 조합을 식별하는 정규화 키(정렬된 userId 결합). 재소개 방지 유니크 키. */
	@Column(name = "member_key", nullable = false, length = 255)
	val memberKey: String,

	@Column(name = "introduced_date", nullable = false)
	val introducedDate: LocalDate,

	/** 소개 만료 시각. 이 시각 이후로는 만료된 소개로 본다. */
	@Column(name = "expires_at", nullable = false)
	val expiresAt: LocalDateTime,

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	var status: MatchStatus = MatchStatus.PROPOSED,

	/** 소개가 생성된 경로(일일 배치/온보딩/필수 신청). [com.org.meeple.common.match.SoloMatchType] */
	@Enumerated(EnumType.STRING)
	@Column(name = "match_type", nullable = false, columnDefinition = "varchar(50)")
	val matchType: SoloMatchType,

	/** 소개팅 신청에 드는 코인 비용. [com.org.meeple.common.coin.CoinUsageType.DATING_INIT]에서 가져온다. */
	@Column(name = "date_init_amount", nullable = false)
	val dateInitAmount: Int,

	/** 소개팅 수락에 드는 코인 비용. [com.org.meeple.common.coin.CoinUsageType.DATING_ACCEPT]에서 가져온다. */
	@Column(name = "date_accept_amount", nullable = false)
	val dateAcceptAmount: Int,
) : BaseEntity()
