package com.org.meeple.core.alarm.command.application

import com.org.meeple.core.alarm.command.application.port.`in`.SaveAlarmUseCase
import com.org.meeple.core.alarm.command.application.port.`in`.command.SaveAlarmCommand
import com.org.meeple.core.alarm.command.application.port.out.SaveAlarmPort
import com.org.meeple.core.alarm.command.domain.Alarm
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [SaveAlarmUseCase] 구현.
 * 입력을 도메인 모델([Alarm.create])로 만들어 저장한다.
 */
@Service
@Transactional
class SaveAlarmService(
	private val saveAlarmPort: SaveAlarmPort,
) : SaveAlarmUseCase {

	override fun save(command: SaveAlarmCommand): Alarm =
		saveAlarmPort.save(
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
}
