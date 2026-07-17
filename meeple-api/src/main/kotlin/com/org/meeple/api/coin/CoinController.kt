package com.org.meeple.api.coin

import com.org.meeple.api.coin.response.CoinBalanceResponse
import com.org.meeple.api.coin.response.CoinCheckoutResponse
import com.org.meeple.api.coin.response.CoinHistoryPageResponse
import com.org.meeple.api.coin.response.CoinItemResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.coin.query.service.port.`in`.GetCoinBalanceUseCase
import com.org.meeple.core.coin.query.service.port.`in`.GetCoinCheckoutUseCase
import com.org.meeple.core.coin.query.service.port.`in`.GetCoinHistoriesUseCase
import com.org.meeple.core.coin.query.service.port.`in`.GetCoinShopUseCase
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.payments.query.service.port.`in`.GetPaymentMethodsUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "코인", description = "코인 상점 조회·코인 잔액 조회·코인 거래 내역 조회")
@RestController
@RequestMapping("/coins/v1")
class CoinController(
	private val getCoinBalanceUseCase: GetCoinBalanceUseCase,
	private val getCoinShopUseCase: GetCoinShopUseCase,
	private val getCoinHistoriesUseCase: GetCoinHistoriesUseCase,
	private val getCoinCheckoutUseCase: GetCoinCheckoutUseCase,
	private val getPaymentMethodsUseCase: GetPaymentMethodsUseCase,
) {

	/** 코인 상점에 노출할 전체 코인 상품 목록을 조회한다. */
	@Operation(summary = "코인 상점 조회", description = "코인 상점에 노출할 전체 코인 상품 목록을 조회한다.")
	@GetMapping("/shop")
	fun getCoinShop(): ApiResponse<List<CoinItemResponse>> =
		ApiResponse.success(CoinItemResponse.listOf(getCoinShopUseCase.getCoinShop()))

	/** 코인 구매 직전 체크아웃 데이터(구매할 코인 아이템 + 구매방법)를 조회한다. */
	@Operation(
		summary = "코인 체크아웃 조회",
		description = "구매할 코인 아이템(itemId)과 활성 구매방법(결제수단) 목록을 반환한다. 코인 상품 없음 404(COIN-004).",
	)
	@GetMapping("/checkout")
	fun getCheckout(
		@RequestParam itemId: Long,
	): ApiResponse<CoinCheckoutResponse> =
		ApiResponse.success(
			CoinCheckoutResponse.of(
				getCoinCheckoutUseCase.getCheckout(itemId),
				getPaymentMethodsUseCase.getActiveMethods(),
			),
		)

	/** 현재 로그인 사용자의 코인 잔액을 조회한다. */
	@Operation(summary = "코인 잔액 조회", description = "현재 로그인 사용자의 코인 잔액을 조회한다.")
	@GetMapping("/balance")
	fun getMyBalance(
		@LoginUser user: AuthUser,
	): ApiResponse<CoinBalanceResponse> =
		ApiResponse.success(CoinBalanceResponse.of(getCoinBalanceUseCase.getBalance(user.id)))

	/**
	 * 현재 로그인 사용자의 코인 거래 내역(사용/획득 전체)을 최신순으로 50건씩 조회한다.
	 * [cursor](이전 페이지의 nextCursor)를 넘기면 그보다 과거 구간을 잇는다.
	 */
	@Operation(summary = "코인 거래 내역 조회", description = "사용/획득 거래 내역 전체를 최신순으로 50건씩 반환한다. cursor(이전 응답의 nextCursor)를 지정하면 과거 구간을 페이지네이션한다.")
	@GetMapping("/histories")
	fun getMyHistories(
		@LoginUser user: AuthUser,
		@RequestParam(required = false) cursor: Long?,
	): ApiResponse<CoinHistoryPageResponse> =
		ApiResponse.success(CoinHistoryPageResponse.of(getCoinHistoriesUseCase.getHistories(user.id, cursor)))
}
