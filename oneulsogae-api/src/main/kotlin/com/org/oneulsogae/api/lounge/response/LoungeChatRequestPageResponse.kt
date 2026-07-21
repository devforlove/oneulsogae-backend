package com.org.oneulsogae.api.lounge.response

import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestPage
import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestView
import java.time.LocalDateTime

/**
 * 받은 대화 신청 목록(커서 페이지) 응답.
 * [nextCursor]를 다음 요청의 `cursor`로 그대로 넘기면 이어지는 페이지를 받는다. (다음 페이지가 없으면 null)
 */
data class LoungeChatRequestPageResponse(
	val items: List<LoungeChatRequestItemResponse>,
	/** 신청 한 건을 수락할 때 드는 코인 수. 신청마다 다르지 않은 전역 정책값이라 항목이 아니라 페이지에 한 번만 싣는다. */
	val acceptCoinAmount: Int,
	val hasNext: Boolean,
	val nextCursor: Long?,
) {
	companion object {

		fun of(page: LoungeChatRequestPage): LoungeChatRequestPageResponse =
			LoungeChatRequestPageResponse(
				items = page.values.map { view: LoungeChatRequestView -> LoungeChatRequestItemResponse.of(view) },
				acceptCoinAmount = page.acceptCoinAmount,
				hasNext = page.hasNext,
				nextCursor = page.nextCursor,
			)
	}
}

/** 신청자 카드 한 장. [chatRoomId]는 아직 수락 전(PENDING)이면 null이다. */
data class LoungeChatRequestItemResponse(
	val requestId: Long,
	val userId: Long,
	val nickname: String?,
	val gender: Gender?,
	val age: Int?,
	val status: LoungeChatRequestStatus,
	val chatRoomId: Long?,
	val requestedAt: LocalDateTime,
) {
	companion object {

		fun of(view: LoungeChatRequestView): LoungeChatRequestItemResponse =
			LoungeChatRequestItemResponse(
				requestId = view.requestId,
				userId = view.userId,
				nickname = view.nickname,
				gender = view.gender,
				age = view.age,
				status = view.status,
				chatRoomId = view.chatRoomId,
				requestedAt = view.requestedAt,
			)
	}
}
