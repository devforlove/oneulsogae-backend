package com.org.meeple.infra.match.command.entity

import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction

/**
 * 팀 매칭([TeamMatchEntity])에 참가한 한 팀([TeamEntity])을 (team_match_id, team_id) 한 쌍으로 잇는 중간(join) 엔티티.
 * 한 팀이 여러 팀 매칭에 재활용되므로 team_matches ↔ teams는 M:N이고, 이 테이블이 그 연결을 보관한다. (한 매칭에 두 행=두 팀)
 * **매치별 수락 여부([accepted])는 팀이 아니라 이 행이 보관**한다 — 같은 팀이라도 매치마다 수락 의사가 다르기 때문이다. (한 매칭의 두 팀이 모두 수락하면 성사)
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

	/** 이 매칭에서 이 팀의 수락 여부(팀 단위). 아직 응답 전이면 null. (한 매칭의 두 팀이 모두 수락하면 성사) */
	@Column(name = "accepted")
	var accepted: Boolean? = null,
) : BaseEntity()
