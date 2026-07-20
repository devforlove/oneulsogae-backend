package com.org.oneulsogae.admin.gathering.command.domain

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException

/**
 * 성별로 나뉜 참가비(원). 남([male])·녀([female]) 각각 0원 이상이어야 한다. (0 = 무료)
 * 정상가·할인가가 같은 구조라 하나의 값 객체로 표현한다. (얼리버드는 할인율로 저장하므로 이 값 객체가 아니다)
 * (admin은 core에 의존하지 않으므로 core의 동명 값 객체를 쓰지 않고 자체 정의한다)
 */
data class GatheringFee(
	val male: Int,
	val female: Int,
) {
	init {
		if (male < 0 || female < 0) {
			throw AdminException(AdminErrorCode.GATHERING_INVALID_FEE)
		}
	}

	companion object {

		/**
		 * 선택 티어(할인가)용 생성. 남/녀 둘 다 있으면 값 객체, 둘 다 없으면 null,
		 * 한쪽만 있으면 짝이 맞지 않으므로 [AdminErrorCode.GATHERING_INVALID_FEE]를 던진다.
		 */
		fun optional(male: Int?, female: Int?): GatheringFee? = when {
			male == null && female == null -> null
			male != null && female != null -> GatheringFee(male, female)
			else -> throw AdminException(AdminErrorCode.GATHERING_INVALID_FEE)
		}
	}
}
