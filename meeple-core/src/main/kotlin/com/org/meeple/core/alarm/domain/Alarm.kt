package com.org.meeple.core.alarm.domain

import com.org.meeple.common.alarm.AlarmType
import java.time.LocalDateTime

/**
 * 사용자 알람(알림) 도메인 모델. 수신자([userId])에게 한 건씩 쌓인다.
 * [title]/[description]은 화면에 노출할 문구, [link]는 알람을 눌렀을 때 이동할 대상 경로다.
 * [fromUserId]는 이 알람을 유발한 상대 사용자(예: 관심을 보낸 사람)로, 없을 수 있다.
 * [createdAt]은 생성 시각으로, 영속성(BaseEntity)이 채운다. 저장 전(신규)에는 null이다.
 */
data class Alarm(
	val id: Long = 0,
	val userId: Long,
	val type: AlarmType,
	val title: String,
	val description: String,
	val link: String,
	val fromUserId: Long? = null,
	val isRead: Boolean = false,
	val createdAt: LocalDateTime? = null,
) {

	companion object {

		/** 신규 알람을 생성한다. (읽지 않음 상태로 시작) */
		fun create(
			userId: Long,
			type: AlarmType,
			title: String,
			description: String,
			link: String,
			fromUserId: Long? = null,
		): Alarm =
			Alarm(
				userId = userId,
				type = type,
				title = title,
				description = description,
				link = link,
				fromUserId = fromUserId,
			)
	}
}
