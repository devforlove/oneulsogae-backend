package com.org.meeple.infra.teammatch.command.entity

import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

/**
 * 팀 없는 솔로 유저에게 추천된 결성(ACTIVE) 팀을 가리키는 포인터 엔티티. (표시 전용 — 솔로 유저는 신청 불가)
 * 일일 배치가 user_id 기준으로 교체(upsert)하므로 유저당 한 행만 유지한다. (soft delete·status·만료 없음)
 * 표시 데이터(팀명·팀원 프로필)는 조회 시점에 teams ⋈ team_members ⋈ match_user를 조인해 채운다.
 * (추천 팀이 해체되면 조회 조인의 teams(@SQLRestriction + status=ACTIVE)에서 빠져 자연히 노출되지 않는다)
 */
@Entity
@Table(
	name = "recommended_teams",
	uniqueConstraints = [
		// 솔로 유저당 추천 1개. 일일 배치가 이 키로 교체(upsert)한다.
		UniqueConstraint(name = "ux_user_id", columnNames = ["user_id"]),
	],
	indexes = [
		// 오늘 추천된(recommended_date = today) 유저 제외 seek용. (일일 팀 추천 배치)
		Index(name = "idx_recommended_date", columnList = "recommended_date"),
	],
)
class RecommendedTeamEntity(
	/** 추천을 받는, 팀 없는 솔로 유저. (교체 키이므로 불변) */
	@Column(name = "user_id", nullable = false, updatable = false)
	val userId: Long,

	/** 추천된 ACTIVE(결성) 팀. (교체 시 갱신) */
	@Column(name = "team_id", nullable = false)
	var teamId: Long,

	/** 추천이 생성된 배치 일자. (관측·신선도 확인용, 교체 시 갱신) */
	@Column(name = "recommended_date", nullable = false)
	var recommendedDate: LocalDate,
) : BaseEntity()
