package com.org.oneulsogae.infra.teammatch.command.entity

import com.org.oneulsogae.common.match.TeamStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version
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
@Table(
	name = "teams",
	indexes = [
		// 가까운 추천 후보 탐색(권역·성별·상태 동등 seek)용. 권역마다 1건씩 probe하므로 풀스캔을 없앤다.
		// 셋 다 동등 조건이라 순서는 seek 가능성에 무관하나, 선택도 높은 region_id를 선두에 둔다.
		Index(name = "idx_region_id_gender_status", columnList = "region_id, gender, status"),
	],
)
class TeamEntity(
	/** 팀 이름. */
	@Column(name = "name", nullable = false, length = 50)
	var name: String,

	/** 팀 성별. 팀은 동성으로 구성되므로 팀 단위로 하나의 성별을 가진다. (구성원([TeamMemberEntity]) 성별과 동일) */
	@Enumerated(EnumType.STRING)
	@Column(name = "gender", nullable = false, columnDefinition = "varchar(50)")
	var gender: Gender,

	/** 팀 활동지역 id(regions FK). 초대 시 regionId로 받는다. 표시용 지역명은 응답 시 regions join으로 내려준다. */
	@Column(name = "region_id", nullable = false)
	var regionId: Long,

	/** 팀 소개. */
	@Column(name = "introduction", length = 1000)
	var introduction: String? = null,

	/** 이 팀의 결성 단계. 초대중으로 시작해 팀결성으로 전이하고, 해체되면 비활성화로 전이한다. (매치별 수락은 [MatchedTeamEntity]가 보관) */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	var status: TeamStatus = TeamStatus.INVITING,
) : BaseEntity() {

	/**
	 * 낙관적 락 버전. 같은 팀에 대한 동시 변경(예: 초대받은 사람의 수락 ↔ 초대자의 철회)을 teams 한 행의 버전으로 직렬화한다.
	 * 수락은 userId 락([com.org.oneulsogae.core.common.lock.LockKeyConstraints.TEAM_MEMBERSHIP])으로, 철회·해체·수정은 teamId 락으로
	 * 잠가 락 키가 달라 서로 배제되지 않으므로, 같은 팀 행에 동시에 쓰는 경합은 이 버전으로 막는다(충돌 시 커밋 실패).
	 */
	@Version
	@Column(name = "version", nullable = false)
	var version: Long = 0
}
