package com.org.meeple.notification

import com.org.meeple.common.alarm.AlarmType
import com.org.meeple.core.alarm.command.application.SaveAlarmService
import com.org.meeple.core.alarm.command.application.port.`in`.command.SaveAlarmCommand
import com.org.meeple.core.alarm.command.application.port.out.SaveAlarmPort
import com.org.meeple.core.alarm.command.domain.Alarm
import com.org.meeple.core.notification.command.application.port.`in`.SendAlarmTalkUseCase
import com.org.meeple.core.notification.command.application.port.`in`.command.SendAlarmTalkCommand
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class SaveAlarmGateTest : DescribeSpec({

	// 저장 시 id를 부여해 도메인으로 돌려주는 fake
	class EchoSavePort : SaveAlarmPort {
		override fun save(alarm: Alarm): Alarm = alarm.copy(id = 100L)
	}

	// attempt 호출을 기록하는 fake
	class RecordingSendAlarmTalk : SendAlarmTalkUseCase {
		val attempts: MutableList<SendAlarmTalkCommand> = mutableListOf()
		override fun attempt(command: SendAlarmTalkCommand) {
			attempts.add(command)
		}
	}

	describe("SaveAlarmService.save") {

		it("알림을 저장하고, 저장된 알림 정보로 알림톡 전송을 시도한다") {
			val sendAlarmTalk = RecordingSendAlarmTalk()
			val service = SaveAlarmService(EchoSavePort(), sendAlarmTalk)

			val saved = service.save(
				SaveAlarmCommand(
					userId = 42L,
					type = AlarmType.TEAM_INVITATION_RECEIVED,
					title = "팀 초대 받음",
					description = "초대가 도착했어요",
					link = "/teams/1",
				),
			)

			saved.id shouldBe 100L
			sendAlarmTalk.attempts.size shouldBe 1
			sendAlarmTalk.attempts.first() shouldBe SendAlarmTalkCommand(
				userId = 42L,
				type = AlarmType.TEAM_INVITATION_RECEIVED,
				title = "팀 초대 받음",
				body = "초대가 도착했어요",
			)
		}
	}
})
