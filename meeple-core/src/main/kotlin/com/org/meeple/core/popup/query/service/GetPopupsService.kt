package com.org.meeple.core.popup.query.service

import com.org.meeple.common.coin.CoinGetType
import com.org.meeple.common.coin.CoinPolicy
import com.org.meeple.core.coin.CoinErrorCode
import com.org.meeple.core.coin.command.application.port.`in`.AcquireCoinUseCase
import com.org.meeple.core.coin.command.application.port.`in`.command.AcquireCoinCommand
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.popup.query.dao.GetPrivatePopupDao
import com.org.meeple.core.popup.query.dao.GetPublicPopupDao
import com.org.meeple.core.popup.query.dto.PopupViews
import com.org.meeple.core.popup.query.service.port.`in`.GetPopupsUseCase
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * [GetPopupsUseCase] 구현.
 * 현재 시각([TimeGenerator]) 기준 노출 대상인 팝업을 전역(public)·개인(private) dao로 각각 조회해 합친다.
 * 개인 팝업을 전역 팝업보다 앞에 두고(우선순위 높음), 각 그룹 내부는 display_order 오름차순으로 정렬한다.
 *
 * 노출 목록에 일일 보상(DAILY_REWARD) 팝업이 있으면, 사용자가 그 팝업을 "받은 것"으로 보고
 * 출석 코인을 하루 1회 적립한다([AcquireCoinUseCase]). 출석 코인은 "팝업을 본 시점"에 지급해야 하므로
 * 조회 흐름의 부수효과로 함께 처리한다. (CQRS 조회 무부수효과 원칙의 의도적 예외)
 * 코인 적립은 [AcquireCoinUseCase]의 자체 트랜잭션에서 수행되고, 팝업 조회는 별도 트랜잭션 없이(OSIV) 읽는다.
 * 이미 오늘 출석 코인을 받은 경우([CoinErrorCode.DAILY_COIN_ALREADY_ACQUIRED])엔 일일 보상 팝업을 응답에서 제외한다.
 */
@Service
class GetPopupsService(
	private val getPublicPopupDao: GetPublicPopupDao,
	private val getPrivatePopupDao: GetPrivatePopupDao,
	private val acquireCoinUseCase: AcquireCoinUseCase,
	private val timeGenerator: TimeGenerator,
) : GetPopupsUseCase {

	override fun getVisiblePopups(userId: Long, isNewUser: Boolean): PopupViews {
		val now: LocalDateTime = timeGenerator.now()
		// 개인 팝업을 전역 팝업보다 앞에 두고, 각 그룹은 display_order(동순위 id) 순으로 정렬해 합친다.
		val merged: PopupViews = getPrivatePopupDao.findVisible(now, userId)
			.mergeBefore(getPublicPopupDao.findVisible(now))

		// 신규 유저 팝업은 isNewUser=true인 요청에만 노출한다. (아니면 제외)
		val popups: PopupViews = if (isNewUser) merged else merged.withoutNewUser()

		// 일일 보상 팝업이 없으면 코인 적립 없이 그대로 반환한다.
		if (!popups.hasDailyReward()) {
			return popups
		}

		// 일일 보상 팝업이 노출 중이면 출석 코인을 하루 1회 적립한다.
		// 이미 오늘 받았다면 중복 노출을 막기 위해 일일 보상 팝업을 응답에서 제외한다.
		return if (acquireDailyRewardCoin(userId)) {
			popups
		} else {
			popups.withoutDailyReward()
		}
	}

	/**
	 * 출석 코인을 적립한다. 팝업 조회는 매 호출마다 일어나므로, 이미 오늘 받은 경우
	 * [AcquireCoinUseCase]가 던지는 [CoinErrorCode.DAILY_COIN_ALREADY_ACQUIRED]는 정상 흐름으로 보고 삼킨다.
	 * (명시적 수령 엔드포인트와 달리 중복 호출은 오류가 아니다)
	 *
	 * @return 새로 적립했으면 true, 이미 오늘 받아 적립하지 않았으면 false.
	 */
	private fun acquireDailyRewardCoin(userId: Long): Boolean =
		try {
			acquireCoinUseCase.acquire(
				userId,
				AcquireCoinCommand(
					amount = CoinPolicy.DAILY_REWARD_COIN_AMOUNT,
					coinType = CoinGetType.DAILY,
				),
			)
			true
		} catch (e: BusinessException) {
			if (e.errorCode != CoinErrorCode.DAILY_COIN_ALREADY_ACQUIRED) {
				throw e
			}
			false
		}
}
