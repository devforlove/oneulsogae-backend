package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.infra.alarm.command.entity.AlarmEntity

/**
 * [AlarmEntity] 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다.
 * 기본은 읽지 않은 "관심 받음" 알람이다.
 */
object AlarmEntityFixture {

	fun create(
		userId: Long = 1L,
		type: AlarmType = AlarmType.ONE_TO_ONE_INTEREST_RECEIVED,
		title: String = "새로운 관심",
		description: String = "회원님에게 관심을 보낸 상대가 있어요.",
		link: String = "/",
		fromUserId: Long? = null,
		fromTeamId: Long? = null,
		isRead: Boolean = false,
	): AlarmEntity =
		AlarmEntity(
			userId = userId,
			type = type,
			title = title,
			description = description,
			link = link,
			fromUserId = fromUserId,
			fromTeamId = fromTeamId,
			isRead = isRead,
		)
}
