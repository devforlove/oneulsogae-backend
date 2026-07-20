package com.org.oneulsogae.core.notification.command.application

import com.org.oneulsogae.core.notification.command.application.port.`in`.SendAlarmTalkUseCase
import com.org.oneulsogae.core.notification.command.application.port.`in`.command.SendAlarmTalkCommand
import com.org.oneulsogae.core.notification.command.application.port.out.AlarmTalkSenderPort
import com.org.oneulsogae.core.notification.command.application.port.out.GetNotificationPreferencePort
import com.org.oneulsogae.core.notification.command.domain.NotificationPreference
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [SendAlarmTalkUseCase] 구현. 사용자 설정을 조회해 알림톡 전송 여부를 결정한다(게이트).
 * 설정 행이 없으면 [NotificationPreference.default]로 간주한다. DB 쓰기는 없어 readOnly 트랜잭션.
 * (향후 실제 카카오 전송은 외부 호출이므로, 호출자 트랜잭션 커밋 이후로 분리하는 것을 검토)
 */
@Service
@Transactional(readOnly = true)
class SendAlarmTalkService(
	private val getNotificationPreferencePort: GetNotificationPreferencePort,
	private val alarmTalkSenderPort: AlarmTalkSenderPort,
) : SendAlarmTalkUseCase {

	override fun attempt(command: SendAlarmTalkCommand) {
		val preference: NotificationPreference =
			getNotificationPreferencePort.findByUserId(command.userId)
				?: NotificationPreference.default(command.userId)

		if (preference.allows(command.type.category())) {
			alarmTalkSenderPort.send(command.userId, command.title, command.body)
		}
	}
}
