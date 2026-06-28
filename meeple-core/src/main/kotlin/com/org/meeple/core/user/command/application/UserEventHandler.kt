package com.org.meeple.core.user.command.application

import com.org.meeple.common.coin.CoinGetType
import com.org.meeple.common.coin.CoinPolicy
import com.org.meeple.core.coin.command.application.port.`in`.AcquireCoinUseCase
import com.org.meeple.core.coin.command.application.port.`in`.command.AcquireCoinCommand
import com.org.meeple.core.common.event.MatchProfileSnapshot
import com.org.meeple.core.common.event.UserLoggedIn
import com.org.meeple.core.match.command.application.port.`in`.RecommendMatchUseCase
import com.org.meeple.core.match.command.application.port.`in`.RecommendTeamUseCase
import com.org.meeple.core.match.command.application.port.`in`.SyncMatchUserUseCase
import com.org.meeple.core.user.command.application.port.out.GetUserDetailPort
import com.org.meeple.core.user.command.application.port.out.GetUserPort
import com.org.meeple.core.user.command.domain.User
import com.org.meeple.core.user.command.domain.UserDetail
import com.org.meeple.core.user.command.domain.event.CompanyEmailVerified
import com.org.meeple.core.user.command.domain.event.UserProfileChanged
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * user 도메인 이벤트의 후속 처리를 한곳에서 다루는 핸들러.
 *
 * 프로필/가입 상태 변경([UserProfileChanged])·로그인([UserLoggedIn])을 받아, 자기 도메인 데이터로 매칭 가능 스냅샷을 만들고
 * match 도메인 in-port([SyncMatchUserUseCase])에 위임해 매칭 읽기 모델(match_user)을 동기화한다.
 * (match_user의 적재/삭제 자체는 그 모델을 소유한 match 도메인이 책임진다 — 다른 도메인 동작은 in-port로만 호출)
 * 정합성을 위해 커밋 직전(BEFORE_COMMIT)에 발행 트랜잭션과 같은 트랜잭션으로 동기화한다.
 * (동기화가 실패하면 프로필/가입/로그인 변경도 함께 롤백된다 — 강한 일관성)
 */
@Component
class UserEventHandler(
	private val getUserPort: GetUserPort,
	private val getUserDetailPort: GetUserDetailPort,
	private val syncMatchUserUseCase: SyncMatchUserUseCase,
	private val recommendMatchUseCase: RecommendMatchUseCase,
	private val recommendTeamUseCase: RecommendTeamUseCase,
	private val acquireCoinUseCase: AcquireCoinUseCase,
) {

	/** 프로필/가입 상태 변경 → 매칭 가능 스냅샷을 만들어 match 읽기 모델 동기화에 위임한다. */
	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	fun onUserProfileChanged(event: UserProfileChanged) {
		syncMatchUserUseCase.sync(event.userId, matchProfileSnapshotOf(event.userId))
	}

	/** 로그인 → 매칭 읽기 모델의 마지막 로그인 시각 갱신에 위임한다. */
	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	fun onUserLoggedIn(event: UserLoggedIn) {
		syncMatchUserUseCase.updateLastLogin(event.userId, event.lastLoginAt)
	}

	/**
	 * 회사 이메일 인증으로 온보딩이 막 완료됨 → 가입 축하 코인을 지급하고, 첫 1:1 매칭 소개와 첫 팀 추천 적재를 함께 처리한다. (CQS: 조회 경로가 아니라 인증 완료 시점에 처리)
	 * 매칭 읽기 모델(match_user)이 같은 트랜잭션의 BEFORE_COMMIT 동기화로 적재·커밋된 뒤라야 후보를 고를 수 있으므로
	 * 커밋 이후(AFTER_COMMIT)에 새 트랜잭션(REQUIRES_NEW)으로 처리한다. (지급·소개·적재 실패가 인증을 롤백시키지 않음 — best-effort)
	 * 중복 지급은 이 리스너가 사용자당 1회(justOnboarded)만 발행되는 것에 의존한다.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun onCompanyEmailVerified(event: CompanyEmailVerified) {
		acquireCoinUseCase.acquire(
			event.userId,
			AcquireCoinCommand(CoinPolicy.SIGNUP_REWARD_COIN_AMOUNT, CoinGetType.SIGNUP),
		)
		recommendMatchUseCase.recommend(event.userId)
		recommendTeamUseCase.recommend(event.userId)
	}

	/** 해당 사용자의 현재 상태로 매칭 가능 스냅샷을 만든다. (사용자가 없거나 매칭 불가면 null) */
	private fun matchProfileSnapshotOf(userId: Long): MatchProfileSnapshot? {
		val user: User = getUserPort.findById(userId) ?: return null
		val detail: UserDetail = getUserDetailPort.findByUserId(userId) ?: return null
		return detail.matchProfileSnapshotOrNull(user.status, user.lastLoginAt)
	}
}
