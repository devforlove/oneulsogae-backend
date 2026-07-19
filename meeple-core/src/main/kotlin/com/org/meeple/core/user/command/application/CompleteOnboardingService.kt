package com.org.meeple.core.user.command.application

import com.org.meeple.common.coin.CoinGetType
import com.org.meeple.common.coin.CoinPolicy
import com.org.meeple.core.coin.command.application.port.`in`.AcquireCoinUseCase
import com.org.meeple.core.coin.command.application.port.`in`.CreateCoinBalanceUseCase
import com.org.meeple.core.coin.command.application.port.`in`.command.AcquireCoinCommand
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.event.MatchProfileSnapshot
import com.org.meeple.core.matchuser.command.application.port.`in`.SyncMatchUserUseCase
import com.org.meeple.core.solomatch.command.application.port.`in`.RecommendMatchUseCase
import com.org.meeple.core.teammatch.command.application.port.`in`.RecommendTeamUseCase
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.user.command.application.port.`in`.CompleteOnboardingUseCase
import com.org.meeple.core.user.command.application.port.`in`.UpdateUserDetailUseCase
import com.org.meeple.core.user.command.application.port.`in`.command.UpdateUserDetailCommand
import com.org.meeple.core.user.command.application.port.out.GetUserDetailPort
import com.org.meeple.core.user.command.application.port.out.GetUserPort
import com.org.meeple.core.user.command.application.port.out.SaveUserPort
import com.org.meeple.core.user.command.domain.User
import com.org.meeple.core.user.command.domain.UserDetail
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [CompleteOnboardingUseCase] 구현.
 * 온보딩 입력값(프로필 상세)을 저장([UpdateUserDetailUseCase])하고 코인 잔액 행을 준비(coin 도메인의 [CreateCoinBalanceUseCase])한 뒤,
 * 아직 ONBOARDING인 사용자를 정식 가입(ACTIVE)으로 전환한다.
 *
 * 이번 호출로 막 가입이 완료되면(justOnboarded) 매칭 읽기 모델(match_user)을 먼저 동기화한 뒤
 * 가입 축하 코인 지급·첫 1:1 매칭 추천을 같은 트랜잭션에서 동기로 처리한다. (추천이 match_user를 읽으므로 동기화가 먼저 끝나 있어야 한다)
 */
@Service
class CompleteOnboardingService(
	private val updateUserDetailUseCase: UpdateUserDetailUseCase,
	private val createCoinBalanceUseCase: CreateCoinBalanceUseCase,
	private val getUserPort: GetUserPort,
	private val saveUserPort: SaveUserPort,
	private val getUserDetailPort: GetUserDetailPort,
	private val syncMatchUserUseCase: SyncMatchUserUseCase,
	private val acquireCoinUseCase: AcquireCoinUseCase,
	private val recommendMatchUseCase: RecommendMatchUseCase,
	private val recommendTeamUseCase: RecommendTeamUseCase,
) : CompleteOnboardingUseCase {

	@Transactional
	override fun complete(userId: Long, command: UpdateUserDetailCommand) {
		updateUserDetailUseCase.update(userId, command)
		// 온보딩 단계에서 코인 잔액 행을 미리 준비해, 이후 적립/차감이 항상 기존 행을 갱신하게 한다. (조회 경로는 쓰기를 하지 않음)
		createCoinBalanceUseCase.createIfAbsent(userId)

		// 아직 ONBOARDING이면 정식 가입(ACTIVE)으로 전환하고, 이번 호출로 막 완료됐는지 반환한다.
		val justOnboarded: Boolean = completeSignUpIfOnboarding(userId)

		if (justOnboarded) {
			// 변경된 프로필/가입 상태로 매칭 읽기 모델(match_user)을 동기로 적재한다. (아래 추천이 이 모델을 읽으므로 추천보다 먼저 끝나 있어야 한다)
			syncMatchUser(userId)
			// 가입 축하 코인 지급·첫 1:1 매칭 소개를 같은 트랜잭션에서 동기로 처리한다.
			acquireCoinUseCase.acquire(
				userId,
				AcquireCoinCommand(CoinPolicy.SIGNUP_REWARD_COIN_AMOUNT, CoinGetType.SIGNUP),
			)
			recommendMatchUseCase.recommend(userId)
			recommendTeamUseCase.recommend(userId)
		}
	}

	/** 아직 ONBOARDING이면 정식 가입(ACTIVE)으로 전환하고, 이번 호출로 막 완료됐는지 반환한다. (이미 가입된 사용자면 false) */
	private fun completeSignUpIfOnboarding(userId: Long): Boolean {
		val user: User = getUserPort.findById(userId)
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다: $userId")
		if (user.isRegistered) return false

		saveUserPort.save(user.completeSignUp())
		return true
	}

	/** 변경된 프로필/가입 상태로 매칭 가능 스냅샷을 만들어 match 읽기 모델(match_user)을 동기화한다. (매칭 불가 상태면 스냅샷이 null이라 읽기 모델에서 제거된다) */
	private fun syncMatchUser(userId: Long) {
		val user: User = getUserPort.findById(userId)
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다: $userId")
		val detail: UserDetail = getUserDetailPort.findByUserId(userId)
			?: throw BusinessException(UserErrorCode.USER_DETAIL_NOT_FOUND, "사용자 프로필을 찾을 수 없습니다: $userId")
		val snapshot: MatchProfileSnapshot? = detail.matchProfileSnapshotOrNull(user.status, user.lastLoginAt)
		syncMatchUserUseCase.sync(userId, snapshot)
	}
}
