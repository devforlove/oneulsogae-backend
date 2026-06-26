package com.org.meeple.core.alarm.query.service

import com.org.meeple.core.alarm.query.dao.GetAlarmDao
import com.org.meeple.core.alarm.query.dao.GetAlarmFromDao
import com.org.meeple.core.alarm.query.dao.GetAlarmFromTeamDao
import com.org.meeple.core.alarm.query.dto.AlarmFroms
import com.org.meeple.core.alarm.query.dto.AlarmTeamMembers
import com.org.meeple.core.alarm.query.dto.AlarmViews
import com.org.meeple.core.alarm.query.dto.AlarmsResult
import com.org.meeple.core.alarm.query.service.port.`in`.GetAlarmsUseCase
import com.org.meeple.core.common.time.TimeGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [GetAlarmsUseCase] 구현. (조회 전용 - 쓰기 부수효과를 두지 않는다)
 * 알람이 무한히 쌓이는 것을 막기 위해 최근 [RETENTION_MONTHS]개월 이내 생성된 알람만 생성 시각 최신순으로 조회한다.
 * froms에 채울 발신 유저는 두 갈래다: 알람의 fromUserId(발신 유저)와 fromTeamId(발신 팀)의 구성원들.
 * 발신 팀 구성원 userId를 먼저 모은 뒤 fromUserId와 합쳐, 프로필은 IN 조회 한 번으로 가져온다. (1+N 방지)
 */
@Service
@Transactional(readOnly = true)
class GetAlarmsService(
	private val getAlarmDao: GetAlarmDao,
	private val getAlarmFromDao: GetAlarmFromDao,
	private val getAlarmFromTeamDao: GetAlarmFromTeamDao,
	private val timeGenerator: TimeGenerator,
) : GetAlarmsUseCase {

	override fun getAlarms(userId: Long): AlarmsResult {
		val since: LocalDateTime = timeGenerator.now().minusMonths(RETENTION_MONTHS)
		val alarms: AlarmViews = getAlarmDao.findByUserIdSince(userId, since)

		val teamIds: Set<Long> = alarms.fromTeamIds()
		val teamMembers: AlarmTeamMembers =
			if (teamIds.isEmpty()) AlarmTeamMembers.empty()
			else getAlarmFromTeamDao.findByTeamIds(teamIds)

		// 발신 유저(fromUserId) + 발신 팀 구성원(fromTeamId)의 프로필을 한 번의 IN 조회로 모은다.
		val fromUserIds: Set<Long> = alarms.fromUserIds() + teamMembers.userIds()
		val froms: AlarmFroms =
			if (fromUserIds.isEmpty()) AlarmFroms.empty()
			else getAlarmFromDao.findByUserIds(fromUserIds)

		return AlarmsResult(alarms = alarms, froms = froms, teamMembers = teamMembers)
	}

	companion object {
		/** 조회 대상 보관 기간(개월). 이보다 오래된 알람은 응답에 포함하지 않는다. */
		private const val RETENTION_MONTHS: Long = 1L
	}
}
