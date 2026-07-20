package com.org.oneulsogae.notification

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.core.notification.command.application.SendAlarmTalkService
import com.org.oneulsogae.core.notification.command.application.port.`in`.command.SendAlarmTalkCommand
import com.org.oneulsogae.core.notification.command.application.port.out.AlarmTalkSenderPort
import com.org.oneulsogae.core.notification.command.application.port.out.GetNotificationPreferencePort
import com.org.oneulsogae.core.notification.command.domain.NotificationPreference
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class SendAlarmTalkServiceTest : DescribeSpec({

	// 전송 호출을 기록하는 fake sender
	class RecordingSender : AlarmTalkSenderPort {
		val sent: MutableList<Triple<Long, String, String>> = mutableListOf()
		override fun send(userId: Long, title: String, body: String) {
			sent.add(Triple(userId, title, body))
		}
	}

	// 지정한 설정만 돌려주는 fake (null이면 미존재)
	class FixedPrefPort(private val preference: NotificationPreference?) : GetNotificationPreferencePort {
		override fun findByUserId(userId: Long): NotificationPreference? = preference
	}

	fun command(type: AlarmType): SendAlarmTalkCommand =
		SendAlarmTalkCommand(userId = 1L, type = type, title = "제목", body = "본문")

	describe("SendAlarmTalkService.attempt") {

		context("해당 카테고리가 켜져 있으면") {
			it("알림톡을 보낸다") {
				val sender = RecordingSender()
				val service = SendAlarmTalkService(
					FixedPrefPort(NotificationPreference(userId = 1L, push = true, team = true)),
					sender,
				)

				service.attempt(command(AlarmType.TEAM_INVITATION_RECEIVED))

				sender.sent.size shouldBe 1
				sender.sent.first() shouldBe Triple(1L, "제목", "본문")
			}
		}

		context("해당 카테고리가 꺼져 있으면") {
			it("보내지 않는다") {
				val sender = RecordingSender()
				val service = SendAlarmTalkService(
					FixedPrefPort(NotificationPreference(userId = 1L, push = true, team = false)),
					sender,
				)

				service.attempt(command(AlarmType.TEAM_INVITATION_RECEIVED))

				sender.sent.size shouldBe 0
			}
		}

		context("push 마스터가 꺼져 있으면") {
			it("카테고리가 켜져 있어도 보내지 않는다") {
				val sender = RecordingSender()
				val service = SendAlarmTalkService(
					FixedPrefPort(NotificationPreference(userId = 1L, push = false, oneToOne = true)),
					sender,
				)

				service.attempt(command(AlarmType.ONE_TO_ONE_MATCHED))

				sender.sent.size shouldBe 0
			}
		}

		context("설정 행이 없으면") {
			it("기본값(켜짐)으로 간주해 보낸다") {
				val sender = RecordingSender()
				val service = SendAlarmTalkService(FixedPrefPort(null), sender)

				service.attempt(command(AlarmType.ONE_TO_ONE_MATCHED))

				sender.sent.size shouldBe 1
			}
		}
	}
})
