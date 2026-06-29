package com.org.meeple.core.user.command.application

import com.org.meeple.common.coin.CoinGetType
import com.org.meeple.common.coin.CoinPolicy
import com.org.meeple.core.coin.command.application.port.`in`.AcquireCoinUseCase
import com.org.meeple.core.coin.command.application.port.`in`.command.AcquireCoinCommand
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.event.MatchProfileSnapshot
import com.org.meeple.core.solomatch.command.application.port.`in`.RecommendMatchUseCase
import com.org.meeple.core.teammatch.command.application.port.`in`.RecommendTeamUseCase
import com.org.meeple.core.matchuser.command.application.port.`in`.SyncMatchUserUseCase
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.user.query.service.port.`in`.GetUserCompanyUseCase
import com.org.meeple.core.user.command.application.port.`in`.VerifyCompanyEmailUseCase
import com.org.meeple.core.user.command.application.port.`in`.result.VerifyCompanyEmailResult
import com.org.meeple.core.user.command.application.port.out.GetCompanyEmailVerificationPort
import com.org.meeple.core.user.command.application.port.out.GetUserDetailPort
import com.org.meeple.core.user.command.application.port.out.GetUserPort
import com.org.meeple.core.user.command.application.port.out.SaveCompanyEmailVerificationPort
import com.org.meeple.core.user.command.application.port.out.SaveUserDetailPort
import com.org.meeple.core.user.command.application.port.out.SaveUserPort
import com.org.meeple.core.user.command.domain.CompanyEmailVerification
import com.org.meeple.core.user.command.domain.User
import com.org.meeple.core.user.command.domain.UserDetail
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [VerifyCompanyEmailUseCase] 구현.
 * 사용자의 가장 최근 인증 요청을 찾아 입력한 인증번호와 비교한다. (재전송으로 누적된 옛 코드는 자동 무효)
 * 일치/미만료/미사용을 확인한 뒤 회사명을 조회([GetUserCompanyUseCase])해 회사 이메일·회사명을 프로필에 확정 반영하고,
 * 인증번호를 사용 처리한다.
 * 아직 ONBOARDING 상태인 사용자라면 이 시점에 정식 가입(ACTIVE)으로 전환한다.
 */
@Service
class VerifyCompanyEmailService(
	private val getCompanyEmailVerificationPort: GetCompanyEmailVerificationPort,
	private val saveCompanyEmailVerificationPort: SaveCompanyEmailVerificationPort,
	private val getUserCompanyUseCase: GetUserCompanyUseCase,
	private val getUserDetailPort: GetUserDetailPort,
	private val saveUserDetailPort: SaveUserDetailPort,
	private val getUserPort: GetUserPort,
	private val saveUserPort: SaveUserPort,
	private val timeGenerator: TimeGenerator,
	private val syncMatchUserUseCase: SyncMatchUserUseCase,
	private val acquireCoinUseCase: AcquireCoinUseCase,
	private val recommendMatchUseCase: RecommendMatchUseCase,
	private val recommendTeamUseCase: RecommendTeamUseCase,
) : VerifyCompanyEmailUseCase {

	@Transactional
	override fun verify(userId: Long, code: String): VerifyCompanyEmailResult {
		val now: LocalDateTime = timeGenerator.now()

		// 사용자의 가장 최근 인증 요청을 조회한다. (없으면 예외)
		val verification: CompanyEmailVerification = getCompanyEmailVerificationPort.findLatestByUserId(userId)
			?: throw BusinessException(UserErrorCode.VERIFICATION_NOT_FOUND)
		verification.validate(code, now)

		// 인증번호를 사용(검증) 처리해 재사용을 막는다.
		saveCompanyEmailVerificationPort.save(verification.verify(now))

		// 회사 이메일 도메인으로 회사명을 조회한다. (매핑이 없으면 null)
		val companyName: String? = getUserCompanyUseCase.findCompanyNameByEmail(verification.companyEmail)
		confirmCompanyOnProfile(verification.userId, verification.companyEmail, companyName)

		// 회사명 조회 결과에 따라 사용자 가입 상태(ACTIVE / COMPANY_NOT_RESOLVED)를 확정한다. (이번 호출로 온보딩이 막 완료됐는지 반환)
		val justOnboarded: Boolean = finalizeStatus(verification.userId, companyName)

		// 변경된 프로필/가입 상태로 매칭 읽기 모델(match_user)을 동기로 적재한다. (아래 추천이 이 모델을 읽으므로 추천보다 먼저 끝나 있어야 한다)
		syncMatchUser(verification.userId)

		// 온보딩이 막 완료됐다면 가입 축하 코인 지급·첫 1:1 매칭 소개·첫 팀 추천 적재를 같은 트랜잭션에서 동기로 처리한다.
		if (justOnboarded) {
			acquireCoinUseCase.acquire(
				verification.userId,
				AcquireCoinCommand(CoinPolicy.SIGNUP_REWARD_COIN_AMOUNT, CoinGetType.SIGNUP),
			)
			recommendMatchUseCase.recommend(verification.userId)
			recommendTeamUseCase.recommend(verification.userId)
		}

		// 막 온보딩됐다면 프론트가 가입 축하 팝업을 띄울 수 있도록 지급 코인 수량을 신호로 함께 내려준다.
		val rewardCoin: Int = if (justOnboarded) CoinPolicy.SIGNUP_REWARD_COIN_AMOUNT else 0
		return VerifyCompanyEmailResult(companyName, justOnboarded, rewardCoin)
	}

	// 검증을 마친 회사 이메일과 (조회한) 회사명을 프로필에 확정 반영한다. (회사명을 못 찾았으면 null)
	private fun confirmCompanyOnProfile(userId: Long, companyEmail: String, companyName: String?) {
		val detail: UserDetail = getUserDetailPort.findByUserId(userId)
			?: throw BusinessException(UserErrorCode.USER_DETAIL_NOT_FOUND, "사용자 프로필을 찾을 수 없습니다: $userId")
		saveUserDetailPort.save(detail.copy(companyEmail = companyEmail, companyName = companyName))
	}

	/** 가입 상태를 확정하고, 이번 호출로 온보딩(정식 가입)이 막 완료됐는지 반환한다. (이미 가입된 사용자면 false) */
	private fun finalizeStatus(userId: Long, companyName: String?): Boolean {
		val user: User = getUserPort.findById(userId)
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다: $userId")

		if (user.isRegistered) return false

		val updated: User = if (companyName != null) user.completeSignUp() else user.markCompanyNotResolved()
		saveUserPort.save(updated)
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
