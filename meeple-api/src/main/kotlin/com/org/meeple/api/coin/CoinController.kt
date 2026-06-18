package com.org.meeple.api.coin

import com.org.meeple.api.coin.request.AcquireCoinRequest
import com.org.meeple.api.coin.response.CoinBalanceResponse
import com.org.meeple.api.coin.response.CoinItemResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.coin.command.application.port.`in`.AcquireCoinUseCase
import com.org.meeple.core.coin.query.service.port.`in`.GetCoinBalanceUseCase
import com.org.meeple.core.coin.query.service.port.`in`.GetCoinShopUseCase
import com.org.meeple.core.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/coins/v1")
class CoinController(
	private val acquireCoinUseCase: AcquireCoinUseCase,
	private val getCoinBalanceUseCase: GetCoinBalanceUseCase,
	private val getCoinShopUseCase: GetCoinShopUseCase,
) {

	/** 코인 상점에 노출할 전체 코인 상품 목록을 조회한다. */
	@GetMapping("/shop")
	fun getCoinShop(): ApiResponse<List<CoinItemResponse>> =
		ApiResponse.success(CoinItemResponse.listOf(getCoinShopUseCase.getCoinShop()))

	/** 현재 로그인 사용자가 코인을 구매/무료 획득하여 적립한다. 적립 후 잔액을 반환한다. */
	@PostMapping("/acquisitions")
	fun acquireCoin(
		@LoginUser user: AuthUser,
		@Valid @RequestBody request: AcquireCoinRequest,
	): ApiResponse<CoinBalanceResponse> =
		ApiResponse.success(CoinBalanceResponse.of(acquireCoinUseCase.acquire(user.id, request.toCommand())))

	/** 현재 로그인 사용자의 코인 잔액을 조회한다. */
	@GetMapping("/balance")
	fun getMyBalance(
		@LoginUser user: AuthUser,
	): ApiResponse<CoinBalanceResponse> =
		ApiResponse.success(CoinBalanceResponse.of(getCoinBalanceUseCase.getBalance(user.id)))
}
