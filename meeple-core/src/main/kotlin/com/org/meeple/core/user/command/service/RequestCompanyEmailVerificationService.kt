package com.org.meeple.core.user.command.service

import com.org.meeple.core.coin.application.port.`in`.CreateCoinBalanceUseCase
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.user.command.service.port.`in`.UpdateUserDetailUseCase
import com.org.meeple.core.user.command.service.port.`in`.command.UpdateUserDetailCommand
import com.org.meeple.core.user.command.service.port.`in`.RequestCompanyEmailVerificationUseCase
import com.org.meeple.core.user.command.service.port.out.GetUserPort
import com.org.meeple.core.user.command.service.port.out.SaveCompanyEmailVerificationPort
import com.org.meeple.core.user.command.service.port.out.SaveUserPort
import com.org.meeple.core.user.command.service.port.out.SendCompanyEmailVerificationPort
import com.org.meeple.core.user.command.domain.CompanyEmailVerification
import com.org.meeple.core.user.command.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom

/**
 * [RequestCompanyEmailVerificationUseCase] 구현.
 * 온보딩 입력값(프로필 상세)을 먼저 저장([UpdateUserDetailUseCase])하고
 * 코인 잔액 행을 준비(coin 도메인의 [CreateCoinBalanceUseCase])한 뒤,
 * 입력한 회사 이메일로 1회용 인증번호를 생성·발송한다.
 *
 * - 아직 ONBOARDING인 사용자라면 회사 이메일 인증 단계(EMAIL_VERIFICATION_PENDING)로 전환한다.
 *   정식 가입(ACTIVE) 전환은 인증 완료 시에만 이뤄진다.
 * - companyName은 인증 완료 시점([VerifyCompanyEmailService])에만 채워진다.
 */
@Service
class RequestCompanyEmailVerificationService(
	private val updateUserDetailUseCase: UpdateUserDetailUseCase,
	private val createCoinBalanceUseCase: CreateCoinBalanceUseCase,
	private val getUserPort: GetUserPort,
	private val saveUserPort: SaveUserPort,
	private val saveCompanyEmailVerificationPort: SaveCompanyEmailVerificationPort,
	private val sendCompanyEmailVerificationPort: SendCompanyEmailVerificationPort,
	private val timeGenerator: TimeGenerator,
) : RequestCompanyEmailVerificationUseCase {

	private val secureRandom = SecureRandom()

	@Transactional
	override fun request(userId: Long, command: UpdateUserDetailCommand): CompanyEmailVerification {
		startEmailVerificationIfOnboarding(userId)
		updateUserDetailUseCase.update(userId, command)
		// 온보딩 단계에서 코인 잔액 행을 미리 준비해, 이후 적립/차감이 항상 기존 행을 갱신하게 한다. (조회 경로는 쓰기를 하지 않음)
		createCoinBalanceUseCase.createIfAbsent(userId)

		val code: String = generateCode()
		val verification: CompanyEmailVerification = saveCompanyEmailVerificationPort.save(
			CompanyEmailVerification.create(
				userId = userId,
				companyEmail = command.companyEmail,
				code = code,
				now = timeGenerator.now(),
			),
		)

		sendCompanyEmailVerificationPort.send(command.companyEmail, code)

		return verification
	}

	/** 6자리 숫자 인증번호를 생성한다. (앞자리 0 허용, 예: "007421") */
	private fun generateCode(): String =
		"%06d".format(secureRandom.nextInt(1_000_000))

	/**
	 * 아직 ONBOARDING 상태인 사용자라면 회사 이메일 인증 단계(EMAIL_VERIFICATION_PENDING)로 전환한다.
	 * 이미 인증 진행 중이거나 ACTIVE면 아무것도 하지 않는다. (정식 가입 전환은 인증 완료 시에만)
	 */
	private fun startEmailVerificationIfOnboarding(userId: Long) {
		val user: User = getUserPort.findById(userId)
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다: $userId")

		if (user.isOnboarding) {
			saveUserPort.save(user.startEmailVerification())
		}
	}
}
