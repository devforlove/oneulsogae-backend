package com.org.meeple.core.user.command.domain

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode
import java.time.Duration
import java.time.LocalDateTime

/**
 * 학교 이메일 인증(대학 인증) 도메인 모델.
 * 사용자가 입력한 학교 이메일로 발송한 1회용 인증번호(code)의 상태를 보관한다.
 * 사용자가 앱에서 인증번호를 입력해 검증되면 해당 학교 이메일의 소유가 확인된 것으로 본다.
 * (온보딩/가입 상태와 무관한 선택적 추가 인증이다 — 검증 성공은 학교 정보를 프로필·매칭 읽기 모델에 기록할 뿐 가입 상태를 바꾸지 않는다)
 */
data class UniversityEmailVerification(
	val id: Long = 0,
	val userId: Long,
	val universityEmail: String,
	val code: String,
	val expiresAt: LocalDateTime,
	val verifiedAt: LocalDateTime? = null,
) {

	/**
	 * 입력한 [code]로 이 인증 요청을 검증한다. 어긋나면 사유에 해당하는 [BusinessException]을 던진다.
	 * - 코드 불일치(재전송된 이전 코드 포함): [UserErrorCode.VERIFICATION_CODE_MISMATCH]
	 * - 이미 검증 완료: [UserErrorCode.VERIFICATION_ALREADY_VERIFIED]
	 * - 만료([now] 기준): [UserErrorCode.VERIFICATION_EXPIRED]
	 */
	fun validate(code: String, now: LocalDateTime) {
		if (this.code != code) {
			throw BusinessException(UserErrorCode.VERIFICATION_CODE_MISMATCH)
		}
		if (isVerified) {
			throw BusinessException(UserErrorCode.VERIFICATION_ALREADY_VERIFIED)
		}
		if (isExpired(now)) {
			throw BusinessException(UserErrorCode.VERIFICATION_EXPIRED)
		}
	}

	/** 이미 검증을 마친 인증번호인지 여부. */
	val isVerified: Boolean
		get() = verifiedAt != null

	/** 만료된 인증번호인지 여부. */
	fun isExpired(now: LocalDateTime): Boolean =
		expiresAt.isBefore(now)

	/** 인증번호를 검증 완료 처리한다. (verifiedAt 기록) */
	fun verify(at: LocalDateTime): UniversityEmailVerification =
		copy(verifiedAt = at)

	companion object {

		/** 인증번호의 유효 기간. */
		val CODE_TTL: Duration = Duration.ofMinutes(10)

		/** 신규 인증 요청을 생성한다. (학교 이메일 검증 후, 만료 시각 = now + [CODE_TTL]) */
		fun create(
			userId: Long,
			universityEmail: String,
			code: String,
			now: LocalDateTime,
		): UniversityEmailVerification {
			validateUniversityEmail(universityEmail)
			return UniversityEmailVerification(
				userId = userId,
				universityEmail = universityEmail,
				code = code,
				expiresAt = now.plus(CODE_TTL),
			)
		}

		/**
		 * 학교 이메일이 학교 인증에 쓸 수 있는지 검증한다.
		 * 이메일 도메인(@ 뒤, 대소문자 무시)이 개인/무료 메일 도메인([CompanyEmailVerification.PERSONAL_EMAIL_DOMAINS])이면
		 * [UserErrorCode.PERSONAL_EMAIL_NOT_ALLOWED]를 던진다. (개인 메일 차단 규칙은 직장 인증과 동일하다)
		 */
		fun validateUniversityEmail(universityEmail: String) {
			val domain: String = universityEmail.substringAfterLast('@').lowercase()
			if (domain in CompanyEmailVerification.PERSONAL_EMAIL_DOMAINS) {
				throw BusinessException(UserErrorCode.PERSONAL_EMAIL_NOT_ALLOWED)
			}
		}
	}
}
