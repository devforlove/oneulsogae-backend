package com.org.meeple.scheduler.common.command.application.port.out

/**
 * 일일 배치에서 끝까지 소개를 받지 못한 대상에게 "오늘 소개 없음" 알람을 보내기 위한 아웃포트.
 * 알람 도메인/영속성은 core·infra가 갖고 있으므로, scheduler는 자기 관점의 이 포트만 정의하고
 * 실제 구현(core의 알람 저장 위임 + 문구·수신자 산정)은 infra 어댑터가 담당한다. (scheduler는 core에 의존하지 않는다)
 * 한 수신자의 실패가 배치나 다른 수신자에 전파되지 않도록, 구현은 수신자 단위로 best-effort 격리한다.
 */
interface NoIntroductionAlarmPort {

	/** 솔로 매칭에서 오늘 소개받지 못한 [userIds] 각자에게 알람을 보낸다. */
	fun notifySoloUnmatched(userIds: Collection<Long>)

	/** 팀 매칭에서 오늘 소개받지 못한 [teamIds] 각 팀의 활성 구성원 전원에게 알람을 보낸다. */
	fun notifyTeamUnmatched(teamIds: Collection<Long>)
}
