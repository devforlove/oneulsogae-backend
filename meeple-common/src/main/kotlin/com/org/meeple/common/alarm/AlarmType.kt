package com.org.meeple.common.alarm

import com.org.meeple.common.notification.NotificationCategory

/** 알람(알림) 유형. */
enum class AlarmType(val description: String) {

	/** [1:1 매칭] 상대가 나에게 관심을 보냄. */
	ONE_TO_ONE_INTEREST_RECEIVED("관심 받음"),

	/** [1:1 매칭] 내가 관심을 보낸 매칭을 상대가 확인함. (관심을 보낸 사람에게) */
	ONE_TO_ONE_MATCH_CHECKED("매칭 확인"),

	/** [1:1 매칭] 매칭이 성사됨. */
	ONE_TO_ONE_MATCHED("매칭 성사"),

	/** [1:1 매칭] 성사된 매칭을 상대가 종료(나감). (방에 남는 상대에게) */
	ONE_TO_ONE_MATCH_ENDED("매칭 종료"),

	/** [1:1 매칭] 오늘 일일 배치에서 소개할 상대를 찾지 못함. (자격은 됐으나 미매칭인 유저에게) */
	ONE_TO_ONE_NO_MATCH_TODAY("오늘 소개 없음"),

	/** [다대다 매칭] 상대가 나에게 관심을 보냄. */
	MANY_TO_MANY_INTEREST_RECEIVED("관심 받음"),

	/** [다대다 매칭] 매칭이 성사됨. */
	MANY_TO_MANY_MATCHED("매칭 성사"),

	/** [다대다 매칭] 성사된 매칭을 상대 팀이 종료(나감). (방에 남는 상대 팀에게) */
	MANY_TO_MANY_MATCH_ENDED("매칭 종료"),

	/** [다대다 매칭] 오늘 일일 배치에서 우리 팀과 소개할 상대 팀을 찾지 못함. (미매칭 팀의 활성 구성원에게) */
	MANY_TO_MANY_NO_MATCH_TODAY("오늘 소개 없음"),

	/** [팀 매칭] 팀에 초대받음. */
	TEAM_INVITATION_RECEIVED("팀 초대 받음"),

	/** [팀 매칭] 보낸 초대가 거절됨(초대받은 사람이 거절 → 초대자에게). */
	TEAM_INVITATION_DECLINED("팀 초대 거절됨"),

	/** [팀 매칭] 받은 초대가 취소됨(초대자가 취소 → 초대받은 사람에게). */
	TEAM_INVITATION_CANCELED("팀 초대 취소됨"),

	/** [팀 매칭] 보낸 초대가 수락됨(초대받은 사람이 수락 → 초대자에게). */
	TEAM_INVITATION_ACCEPTED("팀 초대 수락됨"),

	/** [팀 매칭] 팀이 해체됨(해체를 실행한 구성원을 제외한 같은 팀의 남은 구성원에게). */
	TEAM_DISBANDED("팀 해체됨"),

	/** [코인] 출석(DAILY) 코인이 적립됨. (본인에게, 인앱 전용 — 알림톡 push 없음) */
	COIN_DAILY_ACQUIRED("코인 적립"),
	;

	/** 이 알람 유형이 속한 알림 설정 카테고리. (알림톡 전송 게이트가 이 값으로 사용자 설정을 평가) */
	fun category(): NotificationCategory =
		when (this) {
			ONE_TO_ONE_INTEREST_RECEIVED, ONE_TO_ONE_MATCH_CHECKED, ONE_TO_ONE_MATCHED, ONE_TO_ONE_MATCH_ENDED, ONE_TO_ONE_NO_MATCH_TODAY ->
				NotificationCategory.ONE_TO_ONE
			MANY_TO_MANY_INTEREST_RECEIVED, MANY_TO_MANY_MATCHED, MANY_TO_MANY_MATCH_ENDED, MANY_TO_MANY_NO_MATCH_TODAY ->
				NotificationCategory.MEETING
			TEAM_INVITATION_RECEIVED, TEAM_INVITATION_DECLINED, TEAM_INVITATION_CANCELED, TEAM_INVITATION_ACCEPTED, TEAM_DISBANDED ->
				NotificationCategory.TEAM
			COIN_DAILY_ACQUIRED ->
				NotificationCategory.COIN
		}
}
