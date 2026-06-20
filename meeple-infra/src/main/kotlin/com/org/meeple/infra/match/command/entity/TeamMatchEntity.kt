package com.org.meeple.infra.match.command.entity

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.TeamMatchType
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 2:2(팀) 매칭의 헤더 영속성 엔티티. 먼저 독립적으로 결성된 두 팀([TeamEntity])을 소개로 묶는다.
 * (1:1 솔로 매칭은 [SoloMatchEntity]가 담당하는 별도 애그리거트다)
 * 참가 팀(과 매치별 수락 여부)은 [MatchedTeamEntity]가 join으로 보관한다 — 한 팀이 여러 매치에 재활용되기 때문이다.
 * 두 팀 조합의 정규화 키([memberKey] — 정렬된 team-id 결합)에 유니크 제약을 걸어 같은 팀 조합의 중복 소개를 차단한다. (재소개 방지)
 * introduced_date로 "하루에 한 번만 소개" 제약을 판단한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "team_matches",
	uniqueConstraints = [
		UniqueConstraint(name = "udx_team_match_member_key", columnNames = ["member_key"]),
	],
)
class TeamMatchEntity(
	/** 두 팀 조합을 식별하는 정규화 키(정렬된 team-id 결합). 재소개 방지 유니크 키. */
	@Column(name = "member_key", nullable = false, length = 255)
	val memberKey: String,

	@Column(name = "introduced_date", nullable = false)
	val introducedDate: LocalDate,

	/** 소개 만료 시각. 이 시각 이후로는 만료된 소개로 본다. */
	@Column(name = "expires_at", nullable = false)
	val expiresAt: LocalDateTime,

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	var status: MatchStatus = MatchStatus.PROPOSED,

	/** 팀 매칭이 생성된 경로(일일 배치/필수 신청). [com.org.meeple.common.match.TeamMatchType] */
	@Enumerated(EnumType.STRING)
	@Column(name = "match_type", nullable = false, length = 20)
	val matchType: TeamMatchType,

	/** 팀 매칭 신청에 드는 코인 비용. */
	@Column(name = "date_init_amount", nullable = false)
	val dateInitAmount: Int,

	/** 팀 매칭 수락에 드는 코인 비용. */
	@Column(name = "date_accept_amount", nullable = false)
	val dateAcceptAmount: Int,
) : BaseEntity()
