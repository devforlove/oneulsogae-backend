package com.org.meeple.infra.match.command.entity

import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * 매치와 무관한 팀의 정체성을 담는 영속성 엔티티. (구성원을 초대해 먼저 결성하는 독립 애그리거트)
 * 팀 구성원은 [TeamMemberEntity]가 1:N으로 보관한다. (team_id로 연결)
 * 한 팀은 여러 팀 매칭에 재활용될 수 있으므로, **매치별 의사(수락 여부)는 팀이 아니라 [MatchedTeamEntity](join)가 보관**한다.
 * (팀에 두면 매치마다 달라지는 수락 상태를 표현할 수 없다)
 * [status]는 매치와 무관한 팀 결성 단계(초대중→팀결성→비활성화)다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(name = "teams")
class TeamEntity(
	/** 팀 이름. */
	@Column(name = "name", nullable = false, length = 50)
	var name: String,

	/** 팀 성별. 팀은 동성으로 구성되므로 팀 단위로 하나의 성별을 가진다. (구성원([TeamMemberEntity]) 성별과 동일) */
	@Enumerated(EnumType.STRING)
	@Column(name = "gender", nullable = false, columnDefinition = "varchar(50)")
	var gender: Gender,

	/** 팀 활동지역(시/도 문자열). 초대 시 요청으로 받는다. */
	@Column(name = "region", nullable = false, length = 100)
	var region: String,

	/** 활동지역 권역 코드(1~5). [region]에서 [com.org.meeple.common.user.Region.resolveAreaCode]로 산출. */
	@Column(name = "region_code", nullable = false)
	var regionCode: Int,

	/** 팀 소개. */
	@Column(name = "introduction", length = 1000)
	var introduction: String? = null,

	/** 이 팀의 결성 단계. 초대중으로 시작해 팀결성으로 전이하고, 해체되면 비활성화로 전이한다. (매치별 수락은 [MatchedTeamEntity]가 보관) */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	var status: TeamStatus = TeamStatus.INVITING,
) : BaseEntity()
