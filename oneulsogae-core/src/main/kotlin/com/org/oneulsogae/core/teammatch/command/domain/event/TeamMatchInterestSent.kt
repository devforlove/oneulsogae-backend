package com.org.oneulsogae.core.teammatch.command.domain.event

/**
 * 팀 매칭에 한 팀이 관심(신청)을 보냈을 때 발행되는 도메인 이벤트.
 * 커밋 이후 알림 처리에 필요한 식별 정보만 담는다.
 * [recipientUserIds]는 알림 수신자(상대 팀의 ACTIVE 구성원), [senderTeamId]는 관심을 보낸 팀이다.
 */
data class TeamMatchInterestSent(
	val teamMatchId: Long,
	val senderTeamId: Long,
	val recipientUserIds: List<Long>,
)
