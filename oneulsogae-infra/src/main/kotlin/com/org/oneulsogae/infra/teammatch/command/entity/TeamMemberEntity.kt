package com.org.oneulsogae.infra.teammatch.command.entity

import com.org.oneulsogae.common.match.TeamMemberStatus
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
 * 2:2 팀 매칭에서 한 팀([TeamEntity])에 속한 구성원 한 명을 (team_id, user_id) 한 쌍의 행으로 정규화한 엔티티.
 * 한 팀에 두 명이 속하므로 teams : team_members = 1 : N(=2)이다. (성별은 팀 단위로 [TeamEntity.gender]가 보관한다)
 * (team_id, user_id) 유니크 제약으로 같은 사용자가 한 팀에 중복 소속되는 것을 막고, (user_id) 인덱스로 사용자별 참가 팀 조회를 커버한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "team_members",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_team_id_user_id", columnNames = ["team_id", "user_id"]),
	],
	indexes = [
		// 사용자별 참가 팀 조회.
		Index(name = "idx_user_id", columnList = "user_id"),
	],
)
class TeamMemberEntity(
	@Column(name = "team_id", nullable = false)
	val teamId: Long,

	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** 구성원 상태. 초대중·활성·비활성을 구분한다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	val status: TeamMemberStatus,
) : BaseEntity()
