package com.org.oneulsogae.core.lounge.query.dto

import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.time.ageAt
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 받은 대화 신청 한 건(read model).
 * 신청자의 닉네임·성별·생년월일은 프로필(user_details)에서 조인해 온 표시용 값이다.
 * [chatRoomId]는 수락으로 생성된 채팅방이며, 아직 수락 전(PENDING)이면 null이다.
 * dao는 [birthday]까지 채우고, 서비스가 [age](만 나이)를 채운다.
 */
data class LoungeChatRequestView(
	val requestId: Long,
	/** 신청자 사용자 id. */
	val userId: Long,
	val nickname: String?,
	val gender: Gender?,
	/** 신청자 생년월일. 응답에는 노출하지 않고 [age] 계산에만 쓴다. */
	val birthday: LocalDate?,
	val status: LoungeChatRequestStatus,
	val chatRoomId: Long?,
	val requestedAt: LocalDateTime,
	/** 신청자 만 나이. 서비스가 [birthday]와 기준일로 채운다. (생년월일이 없으면 null) */
	val age: Int? = null,
) {
	/** dao 투영용 생성자. 나이는 서비스가 채운다. */
	constructor(
		requestId: Long,
		userId: Long,
		nickname: String?,
		gender: Gender?,
		birthday: LocalDate?,
		status: LoungeChatRequestStatus,
		chatRoomId: Long?,
		requestedAt: LocalDateTime,
	) : this(requestId, userId, nickname, gender, birthday, status, chatRoomId, requestedAt, null)

	/** 기준일([today])로 만 나이를 채운 신청을 만든다. */
	fun withAge(today: LocalDate): LoungeChatRequestView =
		copy(age = birthday?.ageAt(today))
}
