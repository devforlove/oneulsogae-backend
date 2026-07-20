package com.org.oneulsogae.infra.teammatch.command.entity

import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.common.match.TeamMatchType
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
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
		UniqueConstraint(name = "ux_member_key", columnNames = ["member_key"]),
	],
	indexes = [
		// 성사(status = MATCHED) 팀 제외 seek용. (일일 팀 매칭 배치)
		Index(name = "idx_status", columnList = "status"),
		// 오늘 소개된(introduced_date = today) 팀 제외 seek용. (일일 팀 매칭 배치)
		Index(name = "idx_introduced_date", columnList = "introduced_date"),
		// 만료 정리 배치: status 등치 + expires_at 범위 조회를 받친다.
		Index(name = "idx_status_expires_at", columnList = "status, expires_at"),
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
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	var status: MatchStatus = MatchStatus.PROPOSED,

	/** 팀 매칭이 생성된 경로(일일 배치/필수 신청). [com.org.oneulsogae.common.match.TeamMatchType] */
	@Enumerated(EnumType.STRING)
	@Column(name = "match_type", nullable = false, columnDefinition = "varchar(50)")
	val matchType: TeamMatchType,

	/** 팀 매칭 신청에 드는 코인 비용. */
	@Column(name = "date_init_amount", nullable = false)
	val dateInitAmount: Int,

	/** 팀 매칭 수락에 드는 코인 비용. */
	@Column(name = "date_accept_amount", nullable = false)
	val dateAcceptAmount: Int,
) : BaseEntity() {

	/**
	 * 낙관적 락 버전. 팀 매칭 애그리거트(헤더 + 참가 팀 matched_teams)에 대한 동시 변경을 직렬화한다.
	 * 참가 팀만 바뀌어 헤더가 dirty하지 않은 변경(예: 팀 탈퇴)도 어댑터가 강제 증가시켜 경합을 감지한다. ([com.org.oneulsogae.infra.teammatch.command.adapter.TeamMatchAdapter.save])
	 */
	@Version
	@Column(name = "version", nullable = false)
	var version: Long = 0
}
