package com.org.meeple.core.alarm.query.service

import com.org.meeple.core.alarm.query.dao.CountUnreadAlarmDao
import com.org.meeple.core.alarm.query.service.port.`in`.GetUnreadAlarmCountUseCase
import com.org.meeple.core.common.time.TimeGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [GetUnreadAlarmCountUseCase] 구현. (조회 전용 - 쓰기 부수효과를 두지 않는다)
 * 메인 화면 배지에 노출할 미수신(읽지 않은) 알람 개수를 센다.
 * 알람 목록([GetAlarmsService])과 동일하게 최근 [RETENTION_MONTHS]개월 이내 생성된 알람만 센다.
 * (개수가 목록보다 많아 "안 읽음 N인데 목록엔 없음"이 되는 불일치를 막는다)
 */
@Service
@Transactional(readOnly = true)
class GetUnreadAlarmCountService(
	private val countUnreadAlarmDao: CountUnreadAlarmDao,
	private val timeGenerator: TimeGenerator,
) : GetUnreadAlarmCountUseCase {

	override fun getUnreadCount(userId: Long): Long {
		val since: LocalDateTime = timeGenerator.now().minusMonths(RETENTION_MONTHS)
		return countUnreadAlarmDao.countUnreadByUserIdSince(userId, since)
	}

	companion object {
		/** 미수신 개수 산정 보관 기간(개월). [GetAlarmsService]의 목록 보관 기간과 일치시켜야 개수-목록이 어긋나지 않는다. */
		private const val RETENTION_MONTHS: Long = 1L
	}
}
