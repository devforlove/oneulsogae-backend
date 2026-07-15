package com.org.meeple.core.user.command.application

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.event.MatchProfileSnapshot
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
import com.org.meeple.core.user.command.domain.CompanyEmailVerification
import com.org.meeple.core.user.command.domain.User
import com.org.meeple.core.user.command.domain.UserDetail
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [VerifyCompanyEmailUseCase] 구현. (마이탭 회사 인증 — 온보딩과 분리된 부가 인증으로, 가입 상태를 바꾸지 않는다.
 * 온보딩 완료·가입 축하 코인·첫 소개는 [CompleteOnboardingService]가 담당한다)
 *
 * 사용자의 가장 최근 인증 요청을 찾아 입력한 인증번호와 비교한다. (재전송으로 누적된 옛 코드는 자동 무효)
 * 일치/미만료/미사용을 확인한 뒤 회사명을 조회([GetUserCompanyUseCase])해 회사 이메일·회사명을 프로필에 확정 반영하고,
 * 인증번호를 사용 처리한다. 회사명은 같은 회사 소개 차단 판정에 쓰이므로 매칭 읽기 모델(match_user)을 함께 동기화한다.
 */
@Service
class VerifyCompanyEmailService(
	private val getCompanyEmailVerificationPort: GetCompanyEmailVerificationPort,
	private val saveCompanyEmailVerificationPort: SaveCompanyEmailVerificationPort,
	private val getUserCompanyUseCase: GetUserCompanyUseCase,
	private val getUserDetailPort: GetUserDetailPort,
	private val saveUserDetailPort: SaveUserDetailPort,
	private val getUserPort: GetUserPort,
	private val timeGenerator: TimeGenerator,
	private val syncMatchUserUseCase: SyncMatchUserUseCase,
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

		// 회사 이메일 도메인으로 회사명을 조회한다. (요청 시점에 등록 도메인만 발급되므로 통상 존재하나, 요청~확정 사이 매핑 삭제 엣지에서 null일 수 있다)
		val companyName: String? = getUserCompanyUseCase.findCompanyNameByEmail(verification.companyEmail)
		confirmCompanyOnProfile(verification.userId, verification.companyEmail, companyName)

		// 회사명은 같은 회사 소개 차단 판정에 쓰이므로, 변경된 프로필로 매칭 읽기 모델(match_user)을 동기화한다.
		syncMatchUser(verification.userId)

		return VerifyCompanyEmailResult(companyName)
	}

	// 검증을 마친 회사 이메일과 (조회한) 회사명을 프로필에 확정 반영한다. (회사명을 못 찾았으면 null)
	private fun confirmCompanyOnProfile(userId: Long, companyEmail: String, companyName: String?) {
		val detail: UserDetail = getUserDetailPort.findByUserId(userId)
			?: throw BusinessException(UserErrorCode.USER_DETAIL_NOT_FOUND, "사용자 프로필을 찾을 수 없습니다: $userId")
		saveUserDetailPort.save(detail.copy(companyEmail = companyEmail, companyName = companyName))
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
