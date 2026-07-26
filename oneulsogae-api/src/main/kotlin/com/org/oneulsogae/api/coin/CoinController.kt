package com.org.oneulsogae.api.coin

import com.org.oneulsogae.api.coin.response.CoinBalanceResponse
import com.org.oneulsogae.api.coin.response.CoinHistoryPageResponse
import com.org.oneulsogae.api.coin.response.CoinItemResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.core.coin.query.service.port.`in`.GetCoinBalanceUseCase
import com.org.oneulsogae.core.coin.query.service.port.`in`.GetCoinHistoriesUseCase
import com.org.oneulsogae.core.coin.query.service.port.`in`.GetCoinShopUseCase
import com.org.oneulsogae.core.common.response.ApiResponse
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
) {

	/** 코인 상점에 노출할 코인 상품 목록을 조회한다. (요청 채널·BOTH 상품 중 이미 산 1회 패키지 제외) */
	@Operation(
		summary = "코인 상점 조회",
		description = "요청 채널(channel=PG|IAP|BOTH)로 파는 코인 상품 목록을 조회한다. 앱은 IAP, 웹은 PG를 넘긴다. 회원당 1회 패키지 중 이미 구매한 상품은 제외된다.",
	)
	@GetMapping("/shop")
	fun getCoinShop(
		@LoginUser user: AuthUser,
		@RequestParam channel: CoinSaleChannel,
	): ApiResponse<List<CoinItemResponse>> =
		ApiResponse.success(CoinItemResponse.listOf(getCoinShopUseCase.getCoinShop(user.id, channel)))

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
