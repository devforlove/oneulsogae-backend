package com.org.oneulsogae.core.lounge.query.dto

import com.org.oneulsogae.common.coin.CoinUsageType
import com.org.oneulsogae.common.user.Gender
import java.time.LocalDate

/**
 * 받은 대화 신청 목록([LoungeChatRequestView])의 커서 페이지(일급 컬렉션).
 * 최신(requestId 내림차순)순 목록과 다음 페이지 존재 여부·커서를 함께 담아, 커서 산출 규칙을 한곳에 응집시킨다.
 */
class LoungeChatRequestPage private constructor(
	/** 현재 페이지의 신청 목록. 최신(requestId 내림차순)순. */
	val values: List<LoungeChatRequestView>,
	/** 다음(더 과거) 페이지가 있는지 여부. */
	val hasNext: Boolean,
	/** 이 목록을 조회한 사용자(뷰어)의 성별. [acceptCoinAmount] 계산에 쓴다. */
	private val viewerGender: Gender?,
) {

	/** 다음(더 과거) 페이지 조회의 기준 커서. 현재 페이지 마지막(가장 오래된) 신청의 requestId이며, 다음 페이지가 없으면 null. */
	val nextCursor: Long?
		get() = if (hasNext) values.lastOrNull()?.requestId else null

	/**
	 * 신청 한 건을 수락할 때 드는 코인 수.
	 * 신청마다 다르지 않고 뷰어(수락 주체) 성별로만 갈리는 값([CoinUsageType.LOUNGE_CHAT_ACCEPT])이라 항목이 아니라 페이지에 한 번만 싣는다.
	 * (실제 차감도 서버가 수락자 성별 기준으로 산출한다 — [com.org.oneulsogae.core.lounge.command.application.AcceptLoungeChatService])
	 */
	val acceptCoinAmount: Int
		get() = CoinUsageType.LOUNGE_CHAT_ACCEPT.coinAmount(viewerGender)

	/** 각 항목의 만 나이를 기준일([today])로 채운 페이지를 만든다. */
	fun withAges(today: LocalDate): LoungeChatRequestPage =
		LoungeChatRequestPage(
			values = values.map { view: LoungeChatRequestView -> view.withAge(today) },
			hasNext = hasNext,
			viewerGender = viewerGender,
		)

	companion object {

		/**
		 * "한 건 더 읽기(size + 1)"로 조회한 행들로 페이지를 만든다.
		 * [rows]가 [size]보다 많으면 다음 페이지가 있는 것으로 보고, 초과분은 잘라낸다.
		 */
		fun of(rows: List<LoungeChatRequestView>, size: Int, viewerGender: Gender?): LoungeChatRequestPage =
			LoungeChatRequestPage(values = rows.take(size), hasNext = rows.size > size, viewerGender = viewerGender)
	}
}
