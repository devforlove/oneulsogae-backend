package com.org.oneulsogae.core.user.command.application

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.command.application.port.`in`.RequestUniversityEmailVerificationUseCase
import com.org.oneulsogae.core.user.command.application.port.out.GetUserDetailPort
import com.org.oneulsogae.core.user.command.application.port.out.SaveUniversityEmailVerificationPort
import com.org.oneulsogae.core.user.command.application.port.out.SendUniversityEmailVerificationPort
import com.org.oneulsogae.core.user.command.domain.UniversityEmailVerification
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserUniversityUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom

/**
 * [RequestUniversityEmailVerificationUseCase] 구현.
 * 입력한 학교 이메일로 1회용 인증번호를 생성·발송한다.
 * 등록된 학교 도메인(user_universities)이 아니면 발송하지 않고 [UserErrorCode.UNIVERSITY_NOT_FOUND]를 던진다.
 * 온보딩/가입과 무관한 선택적 추가 인증이므로 프로필 저장·코인 준비·가입 상태 전이를 하지 않는다.
 */
@Service
class RequestUniversityEmailVerificationService(
	private val getUserUniversityUseCase: GetUserUniversityUseCase,
	private val getUserDetailPort: GetUserDetailPort,
	private val saveUniversityEmailVerificationPort: SaveUniversityEmailVerificationPort,
	private val sendUniversityEmailVerificationPort: SendUniversityEmailVerificationPort,
	private val timeGenerator: TimeGenerator,
) : RequestUniversityEmailVerificationUseCase {

	private val secureRandom = SecureRandom()

	@Transactional
	override fun request(userId: Long, universityEmail: String): UniversityEmailVerification {
		// 다른 사용자가 이미 인증해 쓰고 있는 학교 이메일이면, 인증메일 발송 전에 막는다.
		if (getUserDetailPort.existsUniversityEmailUsedByOther(universityEmail, userId)) {
			throw BusinessException(UserErrorCode.UNIVERSITY_EMAIL_ALREADY_USED)
		}

		val code: String = generateCode()
		// 개인 이메일 차단 검증을 먼저 거친 뒤(create), 등록된 학교 도메인인지 확인한다. (매핑이 없으면 발송하지 않고 예외)
		val verification: UniversityEmailVerification = UniversityEmailVerification.create(
			userId = userId,
			universityEmail = universityEmail,
			code = code,
			now = timeGenerator.now(),
		)
		if (getUserUniversityUseCase.findUniversityNameByEmail(universityEmail) == null) {
			throw BusinessException(UserErrorCode.UNIVERSITY_NOT_FOUND)
		}

		val saved: UniversityEmailVerification = saveUniversityEmailVerificationPort.save(verification)
		sendUniversityEmailVerificationPort.send(universityEmail, code)

		return saved
	}

	/** 6자리 숫자 인증번호를 생성한다. (앞자리 0 허용, 예: "007421") */
	private fun generateCode(): String =
		"%06d".format(secureRandom.nextInt(1_000_000))
}
