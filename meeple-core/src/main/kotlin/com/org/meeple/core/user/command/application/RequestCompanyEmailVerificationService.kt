package com.org.meeple.core.user.command.application

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.user.command.application.port.`in`.RequestCompanyEmailVerificationUseCase
import com.org.meeple.core.user.command.application.port.out.GetUserDetailPort
import com.org.meeple.core.user.command.application.port.out.SaveCompanyEmailVerificationPort
import com.org.meeple.core.user.command.application.port.out.SendCompanyEmailVerificationPort
import com.org.meeple.core.user.command.domain.CompanyEmailVerification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom

/**
 * [RequestCompanyEmailVerificationUseCase] 구현.
 * 입력한 회사 이메일로 1회용 인증번호를 생성·발송한다. (온보딩과 분리된 회사 인증 플로우)
 *
 * 다른 사용자가 이미 인증해 쓰고 있는 회사 이메일이면 부수효과(메일 발송) 전에 막는다.
 * 회사 이메일/회사명 확정은 인증 완료 시점([VerifyCompanyEmailService])에만 이뤄진다.
 */
@Service
class RequestCompanyEmailVerificationService(
	private val getUserDetailPort: GetUserDetailPort,
	private val saveCompanyEmailVerificationPort: SaveCompanyEmailVerificationPort,
	private val sendCompanyEmailVerificationPort: SendCompanyEmailVerificationPort,
	private val timeGenerator: TimeGenerator,
) : RequestCompanyEmailVerificationUseCase {

	private val secureRandom = SecureRandom()

	@Transactional
	override fun request(userId: Long, companyEmail: String): CompanyEmailVerification {
		// 다른 사용자가 이미 인증해 쓰고 있는 회사 이메일이면, 부수효과(메일 발송) 전에 막는다.
		if (getUserDetailPort.existsCompanyEmailUsedByOther(companyEmail, userId)) {
			throw BusinessException(UserErrorCode.COMPANY_EMAIL_ALREADY_USED)
		}

		val code: String = generateCode()
		val verification: CompanyEmailVerification = saveCompanyEmailVerificationPort.save(
			CompanyEmailVerification.create(
				userId = userId,
				companyEmail = companyEmail,
				code = code,
				now = timeGenerator.now(),
			),
		)

		sendCompanyEmailVerificationPort.send(companyEmail, code)

		return verification
	}

	/** 6자리 숫자 인증번호를 생성한다. (앞자리 0 허용, 예: "007421") */
	private fun generateCode(): String =
		"%06d".format(secureRandom.nextInt(1_000_000))
}
