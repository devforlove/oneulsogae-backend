package com.org.oneulsogae.api.lounge.response

import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestPage
import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestView
import java.time.LocalDateTime

/**
 * 내가 받은 대화 신청 목록(커서 페이지) 응답.
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

/**
 * 내가 보낸 대화 신청 목록(커서 페이지) 응답.
 * 보낸 신청은 내가 수락하는 것이 아니므로 수락 비용을 싣지 않는다.
 */
data class SentLoungeChatRequestPageResponse(
	val items: List<LoungeChatRequestItemResponse>,
	val hasNext: Boolean,
	val nextCursor: Long?,
) {
	companion object {

		fun of(page: LoungeChatRequestPage): SentLoungeChatRequestPageResponse =
			SentLoungeChatRequestPageResponse(
				items = page.values.map { view: LoungeChatRequestView -> LoungeChatRequestItemResponse.of(view) },
				hasNext = page.hasNext,
				nextCursor = page.nextCursor,
			)
	}
}

/**
 * 신청 한 건. 받은 목록·보낸 목록이 같은 모양을 공유한다.
 * `partner*`는 **이 신청에서 나의 상대방**이다. 받은 목록에서는 신청자, 보낸 목록에서는 글 작성자다.
 * [chatRoomId]는 아직 수락 전(PENDING)이면 null이다.
 */
data class LoungeChatRequestItemResponse(
	val requestId: Long,
	/** 이 신청이 달린 셀소 글의 id. (글 상세로 이동하는 키) */
	val postId: Long,
	val partnerUserId: Long,
	val partnerNickname: String?,
	val partnerGender: Gender?,
	val partnerAge: Int?,
	/** 상대방 프로필 이미지 코드. (미설정이면 null) */
	val partnerProfileImageCode: String?,
	/** 상대방 활동지역 표시 문자열(시/도 시/군/구). 지역 미설정이면 null. */
	val partnerActivityArea: String?,
	val status: LoungeChatRequestStatus,
	val chatRoomId: Long?,
	val requestedAt: LocalDateTime,
) {
	companion object {

		fun of(view: LoungeChatRequestView): LoungeChatRequestItemResponse =
			LoungeChatRequestItemResponse(
				requestId = view.requestId,
				postId = view.postId,
				partnerUserId = view.partnerUserId,
				partnerNickname = view.partnerNickname,
				partnerGender = view.partnerGender,
				partnerAge = view.partnerAge,
				partnerProfileImageCode = view.partnerProfileImageCode,
				partnerActivityArea = view.partnerActivityArea,
				status = view.status,
				chatRoomId = view.chatRoomId,
				requestedAt = view.requestedAt,
			)
	}
}
