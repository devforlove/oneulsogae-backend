package com.org.meeple.core.match.command.domain

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.user.Gender
import java.time.LocalDateTime

/**
 * 팀([Team])에 속한 구성원 한 명의 정보 도메인 모델.
 * 구성원을 (teamId, userId) 한 쌍으로 정규화해, 팀 결성(초대)·성별 균형 구성을 표현한다.
 * [gender]는 성별 균형 구성·성별 기반 조회용이고, [status]는 구성원의 활성 상태(초대중·활성·비활성)다.
 * ([deletedAt]이 채워지면 소프트 삭제된 구성원이다)
 * 영속성은 [com.org.meeple.infra.match.command.entity.TeamMemberEntity]가 담당한다.
 */
data class TeamMember(
	val id: Long = 0,
	val teamId: Long,
	val userId: Long,
	val gender: Gender,
	val status: TeamMemberStatus = TeamMemberStatus.ACTIVE,
	val deletedAt: LocalDateTime? = null,
)
