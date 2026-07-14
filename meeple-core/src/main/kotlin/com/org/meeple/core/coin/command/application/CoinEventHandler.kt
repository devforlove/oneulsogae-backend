package com.org.meeple.core.coin.command.application

import com.org.meeple.common.alarm.AlarmType
import com.org.meeple.core.alarm.command.application.port.`in`.SaveAlarmUseCase
import com.org.meeple.core.alarm.command.application.port.`in`.command.SaveAlarmCommand
import com.org.meeple.core.coin.command.domain.event.DailyCoinAcquired
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 코인 도메인 이벤트의 후속 알람 처리를 다루는 핸들러.
 *
 * 알람은 부가 효과이므로 커밋 이후(AFTER_COMMIT) 별도 트랜잭션([Propagation.REQUIRES_NEW])으로 best-effort 저장한다.
 * (알람 저장이 실패해도 코인 적립은 롤백되지 않는다)
 * 알람 저장은 alarm 도메인 in-port([SaveAlarmUseCase])로 위임한다.
 */
@Component
class CoinEventHandler(
	private val saveAlarmUseCase: SaveAlarmUseCase,
) {

	/**
	 * 출석(DAILY) 코인 적립 → 본인에게 "코인 적립" 인앱 알림.
	 * [AlarmType.COIN_DAILY_ACQUIRED]는 COIN 카테고리라 알림톡 push는 발송되지 않는다(인앱 전용).
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun onDailyCoinAcquired(event: DailyCoinAcquired) {
		saveAlarmUseCase.save(
			SaveAlarmCommand(
				userId = event.userId,
				type = AlarmType.COIN_DAILY_ACQUIRED,
				title = "코인 적립",
				description = "출석 코인 ${event.amount}개가 적립되었어요.",
				// 알람을 누르면 코인 페이지로 이동한다. (프론트 라우팅에 맞춘 경로)
				link = "/coin",
			),
		)
	}
}
