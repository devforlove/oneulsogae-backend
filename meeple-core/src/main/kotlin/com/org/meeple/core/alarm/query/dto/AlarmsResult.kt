package com.org.meeple.core.alarm.query.dto

/**
 * 내 알람 목록 조회 결과(read model 집합).
 * [alarms]는 알람 목록, [froms]는 그 알람들을 유발한 발신 유저들의 프로필을 중복 없이 모은 정규화 목록이다.
 * 클라이언트는 각 알람의 fromUserId로 [froms]를 찾아 발신 유저 프로필을 매핑한다.
 */
data class AlarmsResult(
	val alarms: AlarmViews,
	val froms: AlarmFroms,
)
