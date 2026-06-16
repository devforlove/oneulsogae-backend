package com.org.meeple.core.user.command.service

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.user.application.port.`in`.GetUserCompanyUseCase
import com.org.meeple.core.user.application.port.`in`.VerifyCompanyEmailUseCase
import com.org.meeple.core.user.application.port.`in`.result.VerifyCompanyEmailResult
import com.org.meeple.core.user.application.port.out.GetCompanyEmailVerificationPort
import com.org.meeple.core.user.application.port.out.GetUserDetailPort
import com.org.meeple.core.user.application.port.out.GetUserPort
import com.org.meeple.core.user.application.port.out.SaveCompanyEmailVerificationPort
import com.org.meeple.core.user.application.port.out.SaveUserDetailPort
import com.org.meeple.core.user.application.port.out.SaveUserPort
import com.org.meeple.core.user.domain.CompanyEmailVerification
import com.org.meeple.core.user.domain.User
import com.org.meeple.core.user.domain.UserDetail
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

		// 회사명 조회 결과에 따라 사용자 가입 상태(ACTIVE / COMPANY_NOT_RESOLVED)를 확정한다.
		finalizeStatus(verification.userId, companyName)

		return VerifyCompanyEmailResult(companyName)
	}

	// 검증을 마친 회사 이메일과 (조회한) 회사명을 프로필에 확정 반영한다. (회사명을 못 찾았으면 null)
	private fun confirmCompanyOnProfile(userId: Long, companyEmail: String, companyName: String?) {
		val detail: UserDetail = getUserDetailPort.findByUserId(userId)
			?: throw BusinessException(UserErrorCode.USER_DETAIL_NOT_FOUND, "사용자 프로필을 찾을 수 없습니다: $userId")
		saveUserDetailPort.save(detail.copy(companyEmail = companyEmail, companyName = companyName))
	}

	private fun finalizeStatus(userId: Long, companyName: String?) {
		val user: User = getUserPort.findById(userId)
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다: $userId")

		if (user.isRegistered) return

		val updated: User = if (companyName != null) user.completeSignUp() else user.markCompanyNotResolved()
		saveUserPort.save(updated)
	}
}
