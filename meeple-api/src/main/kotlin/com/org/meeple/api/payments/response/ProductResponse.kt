package com.org.meeple.api.payments.response

import com.org.meeple.common.user.Gender
import com.org.meeple.core.gathering.query.dto.GatheringDetailView
import com.org.meeple.core.gathering.query.dto.GatheringScheduleView
import java.time.LocalDateTime

/**
 * 체크아웃 상품(모임 일정) 응답. 금액은 정산형으로 내려준다:
 * [price] = 해당 성별 정가, [salePrice] = 서버 확정 실결제가(얼리버드 유효 → 얼리버드가, 소진 → 할인가, 그 외 정가).
 * 할인액 표시는 프론트가 price - salePrice로 계산한다. 매진([soldOut])이어도 조회는 막지 않는다.
 */
data class ProductResponse(
	val gatheringId: Long,
	val scheduleId: Long,
	val gender: Gender,
	val title: String,
	val imageUrl: String?,
	val region: String,
	val startAt: LocalDateTime,
	val price: Int,
	val salePrice: Int,
	val soldOut: Boolean,
) {
	companion object {
		fun of(gathering: GatheringDetailView, schedule: GatheringScheduleView, gender: Gender): ProductResponse =
			ProductResponse(
				gatheringId = gathering.id,
				scheduleId = schedule.id,
				gender = gender,
				title = gathering.title,
				imageUrl = gathering.imageUrl,
				region = gathering.region,
				startAt = schedule.startAt,
				price = schedule.feeFor(gender),
				salePrice = schedule.salePriceFor(gender),
				soldOut = schedule.soldOutFor(gender),
			)
	}
}
