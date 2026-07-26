package com.org.oneulsogae.core.coin.query.service

import com.org.oneulsogae.core.coin.CoinErrorCode
import com.org.oneulsogae.core.coin.query.dao.GetCoinItemDao
import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.coin.query.service.port.`in`.GetCoinCheckoutUseCase
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserByIdUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [GetCoinCheckoutUseCase] 구현.
 * 코인 구매 체크아웃에 필요한 코인 아이템을 조회한다. 없으면 [CoinErrorCode.COIN_ITEM_NOT_FOUND].
 * 기간 한정 상품(validDays != null)이면 유저 가입시각 기준 만료를 재검증해, 만료면 [CoinErrorCode.COIN_ITEM_OFFER_EXPIRED].
 * 이 검증은 PG 최종 승인(capture) 전에 수행돼 만료 상품의 헛된 과금을 막는다(상점 목록 캐시 스테일·직접 호출 대비 결제-전 게이트).
 */
@Service
@Transactional(readOnly = true)
class GetCoinCheckoutService(
	private val getCoinItemDao: GetCoinItemDao,
	private val getUserByIdUseCase: GetUserByIdUseCase,
	private val timeGenerator: TimeGenerator,
) : GetCoinCheckoutUseCase {

	override fun getCheckout(userId: Long, itemId: Long): CoinItem {
		val item: CoinItem = getCoinItemDao.findById(itemId)
			?: throw BusinessException(CoinErrorCode.COIN_ITEM_NOT_FOUND)
		if (item.validDays == null) {
			return item
		}
		val now: LocalDateTime = timeGenerator.now()
		val userCreatedAt: LocalDateTime = getUserByIdUseCase.getById(userId).createdAt
		if (!item.isOfferActiveAt(userCreatedAt, now)) {
			throw BusinessException(CoinErrorCode.COIN_ITEM_OFFER_EXPIRED)
		}
		return item
	}
}
