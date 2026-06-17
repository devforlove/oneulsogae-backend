package com.org.meeple.core.alarm.query.service

import com.org.meeple.core.alarm.query.dao.GetAlarmDao
import com.org.meeple.core.alarm.query.dao.GetAlarmFromDao
import com.org.meeple.core.alarm.query.dto.AlarmFroms
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
 * 알람을 유발한 발신 유저들의 프로필은 fromUserId를 모아 IN 조회 한 번으로 가져온다. (1+N 방지)
 */
@Service
@Transactional(readOnly = true)
class GetAlarmsService(
	private val getAlarmDao: GetAlarmDao,
	private val getAlarmFromDao: GetAlarmFromDao,
	private val timeGenerator: TimeGenerator,
) : GetAlarmsUseCase {

	override fun getAlarms(userId: Long): AlarmsResult {
		val since: LocalDateTime = timeGenerator.now().minusMonths(RETENTION_MONTHS)
		val alarms: AlarmViews = getAlarmDao.findByUserIdSince(userId, since)

		val fromUserIds: Set<Long> = alarms.fromUserIds()
		val froms: AlarmFroms =
			if (fromUserIds.isEmpty()) AlarmFroms.empty()
			else getAlarmFromDao.findByUserIds(fromUserIds)

		return AlarmsResult(alarms = alarms, froms = froms)
	}

	companion object {
		/** 조회 대상 보관 기간(개월). 이보다 오래된 알람은 응답에 포함하지 않는다. */
		private const val RETENTION_MONTHS: Long = 1L
	}
}
