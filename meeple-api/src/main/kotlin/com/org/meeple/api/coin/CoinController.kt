package com.org.meeple.api.coin

import com.org.meeple.api.coin.response.CoinBalanceResponse
import com.org.meeple.api.coin.response.CoinItemResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.coin.query.service.port.`in`.GetCoinBalanceUseCase
import com.org.meeple.core.coin.query.service.port.`in`.GetCoinShopUseCase
import com.org.meeple.core.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "코인", description = "코인 상점 조회·코인 잔액 조회")
@RestController
@RequestMapping("/coins/v1")
class CoinController(
	private val getCoinBalanceUseCase: GetCoinBalanceUseCase,
	private val getCoinShopUseCase: GetCoinShopUseCase,
) {

	/** 코인 상점에 노출할 전체 코인 상품 목록을 조회한다. */
	@Operation(summary = "코인 상점 조회", description = "코인 상점에 노출할 전체 코인 상품 목록을 조회한다.")
	@GetMapping("/shop")
	fun getCoinShop(): ApiResponse<List<CoinItemResponse>> =
		ApiResponse.success(CoinItemResponse.listOf(getCoinShopUseCase.getCoinShop()))

	/** 현재 로그인 사용자의 코인 잔액을 조회한다. */
	@Operation(summary = "코인 잔액 조회", description = "현재 로그인 사용자의 코인 잔액을 조회한다.")
	@GetMapping("/balance")
	fun getMyBalance(
		@LoginUser user: AuthUser,
	): ApiResponse<CoinBalanceResponse> =
		ApiResponse.success(CoinBalanceResponse.of(getCoinBalanceUseCase.getBalance(user.id)))
}
