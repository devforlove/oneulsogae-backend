package com.org.meeple.core.gathering.command.domain

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.gathering.GatheringErrorCode

/**
 * 성별로 나뉜 참가비(원). 남([male])·녀([female]) 각각 0원 이상이어야 한다. (0 = 무료)
 * 정상가·얼리버드 특가·할인가가 모두 같은 구조라 하나의 값 객체로 표현한다.
 */
data class GatheringFee(
	val male: Int,
	val female: Int,
) {
	init {
		if (male < 0 || female < 0) {
			throw BusinessException(GatheringErrorCode.INVALID_FEE)
		}
	}

	companion object {

		/**
		 * 선택 티어(얼리버드 특가·할인가)용 생성. 남/녀 둘 다 있으면 값 객체, 둘 다 없으면 null,
		 * 한쪽만 있으면 짝이 맞지 않으므로 [GatheringErrorCode.INVALID_FEE]를 던진다.
		 */
		fun optional(male: Int?, female: Int?): GatheringFee? = when {
			male == null && female == null -> null
			male != null && female != null -> GatheringFee(male, female)
			else -> throw BusinessException(GatheringErrorCode.INVALID_FEE)
		}
	}
}
