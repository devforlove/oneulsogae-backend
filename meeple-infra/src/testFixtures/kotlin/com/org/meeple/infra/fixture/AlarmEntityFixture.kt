package com.org.meeple.infra.fixture

import com.org.meeple.common.alarm.AlarmType
import com.org.meeple.infra.alarm.entity.AlarmEntity

/**
 * [AlarmEntity] 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다.
 * 기본은 읽지 않은 "관심 받음" 알람이다.
 */
object AlarmEntityFixture {

	fun create(
		userId: Long = 1L,
		type: AlarmType = AlarmType.INTEREST_RECEIVED,
		title: String = "새로운 관심",
		description: String = "회원님에게 관심을 보낸 상대가 있어요.",
		link: String = "/",
		fromUserId: Long? = null,
		isRead: Boolean = false,
	): AlarmEntity =
		AlarmEntity(
			userId = userId,
			type = type,
			title = title,
			description = description,
			link = link,
			fromUserId = fromUserId,
			isRead = isRead,
		)
}
