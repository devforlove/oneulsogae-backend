package com.org.oneulsogae.core.coin.command.domain

import com.org.oneulsogae.common.coin.CoinGetType
import com.org.oneulsogae.common.coin.CoinUsageType
import java.time.LocalDateTime

/**
 * 코인 거래 내역(원장) 도메인 모델. 적립(구매/무료 획득)과 차감(사용)이 한 건씩 쌓인다.
 * 적립은 양수 [amount], 차감은 음수 [amount]로 기록되어, 전체 합(SUM)이 곧 사용자의 잔액이 된다.
 * [coinType]은 적립(획득) 유형으로 차감 내역에는 해당이 없어 null이고, [coinUsageType]은 차감(사용) 유형으로 적립 내역에는 해당이 없어 null이다.
 * 영속성은 [com.org.oneulsogae.infra.coin.command.entity.CoinHistoryEntity]가 담당한다.
 */
data class CoinHistory(
	val id: Long = 0,
	val userId: Long,
	val amount: Int,
	val coinType: CoinGetType? = null,
	val acquiredAt: LocalDateTime,
	val coinUsageType: CoinUsageType? = null,
) {

	companion object {

		/** 사용자가 새로 획득/구매한 코인 적립 내역을 생성한다. (양수 amount) [acquiredAt]은 호출 측에서 주입한다. */
		fun acquire(
			userId: Long,
			amount: Int,
			coinType: CoinGetType,
			acquiredAt: LocalDateTime,
		): CoinHistory =
			CoinHistory(
				userId = userId,
				amount = amount,
				coinType = coinType,
				acquiredAt = acquiredAt,
			)

		/**
		 * 코인을 차감(사용)한 거래 내역을 생성한다.
		 * 잔액 감소분을 음수 amount로 기록해 SUM(coins.amount)가 곧 잔액이 되도록 한다.
		 * @param amount 차감 수량(양수). 원장에는 음수(-amount)로 저장된다.
		 * @param coinUsageType 차감 작업의 유형. (소개팅/미팅 신청·수락)
		 * @param occurredAt 차감 발생 시각. 호출 측에서 주입한다.
		 */
		fun spend(
			userId: Long,
			amount: Int,
			coinUsageType: CoinUsageType,
			occurredAt: LocalDateTime,
		): CoinHistory {
			require(amount > 0) { "차감 수량은 1 이상이어야 합니다." }
			return CoinHistory(
				userId = userId,
				amount = -amount,
				acquiredAt = occurredAt,
				coinUsageType = coinUsageType,
			)
		}
	}
}
