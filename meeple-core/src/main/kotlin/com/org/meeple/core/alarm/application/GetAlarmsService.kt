package com.org.meeple.core.alarm.application

import com.org.meeple.core.alarm.application.port.`in`.GetAlarmsUseCase
import com.org.meeple.core.alarm.application.port.out.GetAlarmPort
import com.org.meeple.core.alarm.domain.Alarms
import com.org.meeple.core.common.time.TimeGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [GetAlarmsUseCase] 구현. (조회 전용)
 * 알람이 무한히 쌓이는 것을 막기 위해 최근 [RETENTION_MONTHS]개월 이내 생성된 알람만 생성 시각 최신순으로 조회한다.
 */
@Service
@Transactional(readOnly = true)
class GetAlarmsService(
	private val getAlarmPort: GetAlarmPort,
	private val timeGenerator: TimeGenerator,
) : GetAlarmsUseCase {

	override fun getAlarms(userId: Long): Alarms {
		val since: LocalDateTime = timeGenerator.now().minusMonths(RETENTION_MONTHS)
		return getAlarmPort.findByUserIdSince(userId, since)
	}

	companion object {
		/** 조회 대상 보관 기간(개월). 이보다 오래된 알람은 응답에 포함하지 않는다. */
		private const val RETENTION_MONTHS: Long = 1L
	}
}
