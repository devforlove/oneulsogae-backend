package com.org.oneulsogae.core.alarm.command.application

import com.org.oneulsogae.core.alarm.command.application.port.`in`.MarkAlarmsReadUseCase
import com.org.oneulsogae.core.alarm.command.application.port.out.MarkAlarmsReadPort
import com.org.oneulsogae.core.common.time.TimeGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [MarkAlarmsReadUseCase] 구현. (명령 경로)
 * 알림 페이지 진입 시 호출되어, 화면에 보이는 알람(최근 [RETENTION_MONTHS]개월 이내)의 읽지 않은 건을 모두 읽음 처리한다.
 * 보관 기간을 목록 조회([com.org.oneulsogae.core.alarm.query.service.GetAlarmsService])와 일치시켜, 화면에 노출되는 알람만 읽음 처리한다.
 */
@Service
@Transactional
class MarkAlarmsReadService(
	private val markAlarmsReadPort: MarkAlarmsReadPort,
	private val timeGenerator: TimeGenerator,
) : MarkAlarmsReadUseCase {

	override fun markAllRead(userId: Long) {
		val since: LocalDateTime = timeGenerator.now().minusMonths(RETENTION_MONTHS)
		markAlarmsReadPort.markAllReadByUserIdSince(userId, since)
	}

	companion object {
		/** 읽음 처리 대상 보관 기간(개월). 알람 목록/미수신 개수 조회 기간과 일치시켜야 화면에 보이는 알람만 읽음 처리된다. */
		private const val RETENTION_MONTHS: Long = 1L
	}
}
