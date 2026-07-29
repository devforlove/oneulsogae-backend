package com.org.oneulsogae.core.user.command.application

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.user.command.application.port.`in`.RequestCompanyEmailVerificationUseCase
import com.org.oneulsogae.core.user.command.application.port.`in`.result.RequestCompanyEmailVerificationResult
import com.org.oneulsogae.core.user.command.application.port.out.GetUserDetailPort
import com.org.oneulsogae.core.user.command.application.port.out.SaveCompanyEmailVerificationPort
import com.org.oneulsogae.core.user.command.application.port.out.SendCompanyEmailVerificationPort
import com.org.oneulsogae.core.user.command.domain.CompanyEmailVerification
import com.org.oneulsogae.core.user.query.dto.UserCompany
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserCompanyUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom

/**
 * [RequestCompanyEmailVerificationUseCase] 구현.
 * 입력한 회사 이메일로 1회용 인증번호를 생성·발송한다. (온보딩과 분리된 회사 인증 플로우)
 *
 * 다른 사용자가 이미 인증해 쓰고 있거나 등록된 회사([GetUserCompanyUseCase])의 도메인이 아니면
 * 부수효과(메일 발송) 전에 막는다. 미등록 회사 직원은 서류 이미지 인증 경로로 안내한다.
 * 같은 도메인을 쓰는 회사가 여럿이면(그룹 계열사 등) 발송 없이 후보 목록을 돌려주고, 사용자가 회사를 지정해 재요청한다.
 * 확정한 회사는 인증 행에 함께 저장해, 인증 완료 시점([VerifyCompanyEmailService])에 도메인을 재조회하지 않는다.
 */
@Service
class RequestCompanyEmailVerificationService(
	private val getUserDetailPort: GetUserDetailPort,
	private val saveCompanyEmailVerificationPort: SaveCompanyEmailVerificationPort,
	private val sendCompanyEmailVerificationPort: SendCompanyEmailVerificationPort,
	private val getUserCompanyUseCase: GetUserCompanyUseCase,
	private val timeGenerator: TimeGenerator,
) : RequestCompanyEmailVerificationUseCase {

	private val secureRandom = SecureRandom()

	@Transactional
	override fun request(userId: Long, companyEmail: String, userCompanyId: Long?): RequestCompanyEmailVerificationResult {
		// 다른 사용자가 이미 인증해 쓰고 있는 회사 이메일이면, 부수효과(메일 발송) 전에 막는다.
		if (getUserDetailPort.existsCompanyEmailUsedByOther(companyEmail, userId)) {
			throw BusinessException(UserErrorCode.COMPANY_EMAIL_ALREADY_USED)
		}

		// 개인 이메일 차단 등 형식 검증을 먼저 거친다. (도메인 검증)
		CompanyEmailVerification.validateCompanyEmail(companyEmail)

		// 등록된 회사(user_companies) 도메인이 아니면 발급·발송 전에 막는다. (학교 인증과 동일한 요청 시점 차단)
		val candidates: List<UserCompany> = getUserCompanyUseCase.findCompaniesByEmail(companyEmail)
		if (candidates.isEmpty()) {
			throw BusinessException(UserErrorCode.COMPANY_NOT_FOUND)
		}

		// 후보가 여럿인데 회사를 지정하지 않았으면, 발송 없이 후보 목록을 돌려줘 선택을 받는다.
		val company: UserCompany = when {
			userCompanyId != null -> candidates.find { candidate: UserCompany -> candidate.id == userCompanyId }
				?: throw BusinessException(UserErrorCode.COMPANY_NOT_FOUND)
			candidates.size == 1 -> candidates.single()
			else -> return RequestCompanyEmailVerificationResult.CompanySelectionRequired(candidates)
		}

		val code: String = generateCode()
		val verification: CompanyEmailVerification = CompanyEmailVerification.create(
			userId = userId,
			companyEmail = companyEmail,
			userCompanyId = company.id,
			code = code,
			now = timeGenerator.now(),
		)

		val saved: CompanyEmailVerification = saveCompanyEmailVerificationPort.save(verification)

		sendCompanyEmailVerificationPort.send(companyEmail, code)

		return RequestCompanyEmailVerificationResult.Sent(saved)
	}

	/** 6자리 숫자 인증번호를 생성한다. (앞자리 0 허용, 예: "007421") */
	private fun generateCode(): String =
		"%06d".format(secureRandom.nextInt(1_000_000))
}
