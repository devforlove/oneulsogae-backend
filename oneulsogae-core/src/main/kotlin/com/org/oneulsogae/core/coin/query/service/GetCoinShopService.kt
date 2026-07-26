package com.org.oneulsogae.core.coin.query.service

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.core.coin.query.dao.GetCoinItemDao
import com.org.oneulsogae.core.coin.query.dto.CoinItems
import com.org.oneulsogae.core.coin.query.service.port.`in`.GetCoinShopUseCase
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserByIdUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [GetCoinShopUseCase] 구현.
 * 요청 채널(+BOTH) 상품 중 이미 구매한 1회 패키지를 제외하고, 기간 한정 상품은 만료된 것을 걸러 반환한다.
 * 만료 판정에 필요한 유저 가입시각은 기간 한정 상품이 실제로 있을 때만 조회한다(상시 상품만이면 유저 조회 생략).
 */
@Service
@Transactional(readOnly = true)
class GetCoinShopService(
	private val getCoinItemDao: GetCoinItemDao,
	private val getUserByIdUseCase: GetUserByIdUseCase,
	private val timeGenerator: TimeGenerator,
) : GetCoinShopUseCase {

	override fun getCoinShop(userId: Long, channel: CoinSaleChannel): CoinItems {
		val items: CoinItems = getCoinItemDao.findShopItems(userId, channel)
		if (!items.hasTimeLimitedOffer()) {
			return items
		}
		val now: LocalDateTime = timeGenerator.now()
		val userCreatedAt: LocalDateTime = getUserByIdUseCase.getById(userId).createdAt
		return items.activeOffersAt(userCreatedAt, now).withExpiryFor(userCreatedAt)
	}
}
