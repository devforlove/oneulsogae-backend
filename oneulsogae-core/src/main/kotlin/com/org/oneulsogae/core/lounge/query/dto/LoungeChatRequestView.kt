package com.org.oneulsogae.core.lounge.query.dto

import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.time.ageAt
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 대화 신청 한 건(read model). 받은 목록·보낸 목록이 같은 모양을 공유한다.
 * 프로필 항목(`partner*`)은 **이 신청에서 나의 상대방**을 가리킨다.
 * 받은 목록에서는 신청자, 보낸 목록에서는 글 작성자다. (조회 방향은 dao가 정한다)
 * [chatRoomId]는 수락으로 생성된 채팅방이며, 아직 수락 전(PENDING)이면 null이다.
 * dao는 [partnerBirthday]까지 채우고, 서비스가 [partnerAge](만 나이)를 채운다.
 */
data class LoungeChatRequestView(
	val requestId: Long,
	/** 이 신청이 달린 셀소 글의 id. (목록에서 글 상세로 이동하는 키) */
	val postId: Long,
	/** 상대방 사용자 id. */
	val partnerUserId: Long,
	val partnerNickname: String?,
	val partnerGender: Gender?,
	/** 상대방 생년월일. 응답에는 노출하지 않고 [partnerAge] 계산에만 쓴다. */
	val partnerBirthday: LocalDate?,
	/** 상대방 프로필 이미지 코드. (미설정이면 null) */
	val partnerProfileImageCode: String?,
	/** 상대방 활동지역 표시 문자열(시/도 시/군/구). 지역 미설정이면 null. */
	val partnerActivityArea: String?,
	val status: LoungeChatRequestStatus,
	val chatRoomId: Long?,
	val requestedAt: LocalDateTime,
	/** 상대방 만 나이. 서비스가 [partnerBirthday]와 기준일로 채운다. (생년월일이 없으면 null) */
	val partnerAge: Int? = null,
) {
	/** dao 투영용 생성자. 나이는 서비스가 채운다. */
	constructor(
		requestId: Long,
		postId: Long,
		partnerUserId: Long,
		partnerNickname: String?,
		partnerGender: Gender?,
		partnerBirthday: LocalDate?,
		partnerProfileImageCode: String?,
		partnerActivityArea: String?,
		status: LoungeChatRequestStatus,
		chatRoomId: Long?,
		requestedAt: LocalDateTime,
	) : this(
		requestId, postId, partnerUserId, partnerNickname, partnerGender, partnerBirthday,
		partnerProfileImageCode, partnerActivityArea, status, chatRoomId, requestedAt, null,
	)

	/** 기준일([today])로 상대방 만 나이를 채운 신청을 만든다. */
	fun withAge(today: LocalDate): LoungeChatRequestView =
		copy(partnerAge = partnerBirthday?.ageAt(today))
}
