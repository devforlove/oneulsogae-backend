package com.org.oneulsogae.core.alarm.command.application

import com.org.oneulsogae.core.alarm.command.application.port.`in`.SaveAlarmUseCase
import com.org.oneulsogae.core.alarm.command.application.port.`in`.command.SaveAlarmCommand
import com.org.oneulsogae.core.alarm.command.application.port.out.SaveAlarmPort
import com.org.oneulsogae.core.alarm.command.domain.Alarm
import com.org.oneulsogae.core.notification.command.application.port.`in`.SendAlarmTalkUseCase
import com.org.oneulsogae.core.notification.command.application.port.`in`.command.SendAlarmTalkCommand
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [SaveAlarmUseCase] 구현.
 * 입력을 도메인 모델([Alarm.create])로 만들어 저장하고, 저장된 알림으로 알림톡 전송을 시도한다.
 * 전송 여부 판단(사용자 설정 게이트)은 notification 도메인([SendAlarmTalkUseCase])에 위임한다.
 */
@Service
@Transactional
class SaveAlarmService(
	private val saveAlarmPort: SaveAlarmPort,
	private val sendAlarmTalkUseCase: SendAlarmTalkUseCase,
) : SaveAlarmUseCase {

	override fun save(command: SaveAlarmCommand): Alarm {
		val saved: Alarm = saveAlarmPort.save(
			Alarm.create(
				userId = command.userId,
				type = command.type,
				title = command.title,
				description = command.description,
				link = command.link,
				fromUserId = command.fromUserId,
				fromTeamId = command.fromTeamId,
			),
		)
		sendAlarmTalkUseCase.attempt(
			SendAlarmTalkCommand(
				userId = saved.userId,
				type = saved.type,
				title = saved.title,
				body = saved.description,
			),
		)
		return saved
	}
}
