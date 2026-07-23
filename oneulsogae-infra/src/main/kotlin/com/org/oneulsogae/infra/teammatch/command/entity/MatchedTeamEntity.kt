package com.org.oneulsogae.infra.teammatch.command.entity

import com.org.oneulsogae.common.match.MatchedTeamStatus
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction

/**
 * 팀 매칭([TeamMatchEntity])에 참가한 한 팀([TeamEntity])을 (team_match_id, team_id) 한 쌍으로 잇는 중간(join) 엔티티.
 * 한 팀이 여러 팀 매칭에 재활용되므로 team_matches ↔ teams는 M:N이고, 이 테이블이 그 연결을 보관한다. (한 매칭에 두 행=두 팀)
 * **매치별 상태([status])는 팀이 아니라 이 행이 보관**한다 — 같은 팀이라도 매치마다 상태가 다르기 때문이다. (WAITING→APPLY→ACTIVE/DEACTIVE, 한 매칭의 두 팀이 모두 신청하면 성사)
 * (team_match_id, team_id) 유니크 제약으로 한 매칭에 같은 팀이 중복 참가하는 것을 막고, (team_id) 인덱스로 팀별 참가 매칭 이력 조회를 커버한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "matched_teams",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_team_match_id_team_id", columnNames = ["team_match_id", "team_id"]),
	],
	indexes = [
		// 팀별 참가 매칭 이력 조회.
		Index(name = "idx_team_id", columnList = "team_id"),
	],
)
class MatchedTeamEntity(
	@Column(name = "team_match_id", nullable = false)
	val teamMatchId: Long,

	@Column(name = "team_id", nullable = false)
	val teamId: Long,

	/** 이 매칭에서 이 팀의 상태(팀 단위). 소개 직후 WAITING으로 시작한다. (한 매칭의 두 팀이 모두 신청하면 성사) */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	var status: MatchedTeamStatus = MatchedTeamStatus.WAITING,

	/** 이 팀이 신청(APPLY)할 때 코인을 지불한 구성원 userId. 미신청이면 null. (미성사 만료 환불 대상 식별용) */
	@Column(name = "applicant_user_id")
	var applicantUserId: Long? = null,

	/** 신청(APPLY) 시 실제 지불한 신청 비용의 스냅샷. 미신청이거나 구행 데이터면 null. (환불 산정에 쓴다) */
	@Column(name = "paid_init_amount")
	var paidInitAmount: Int? = null,
) : BaseEntity()
