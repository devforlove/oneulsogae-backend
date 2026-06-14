package com.org.meeple.core.user.application

import com.org.meeple.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 사용자 도메인 에러 코드.
 * [com.org.meeple.core.common.error.BusinessException]에 넘겨 사용한다.
 */
enum class UserErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	USER_NOT_FOUND("USER-001", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	USER_NOT_ACTIVE("USER-007", "정식 가입(ACTIVE)이 완료되지 않은 사용자입니다.", HttpStatus.BAD_REQUEST),

	// 프로필(user_details)
	USER_DETAIL_NOT_FOUND("USER-011", "사용자 프로필을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	GENDER_REQUIRED("USER-012", "성별을 입력해 주세요.", HttpStatus.BAD_REQUEST),
	REGION_NOT_RESOLVED("USER-013", "활동지역을 인식하지 못했습니다. 지원하는 지역(시/도)을 입력해 주세요.", HttpStatus.BAD_REQUEST),
	EMAIL_ALREADY_REGISTERED("USER-009", "이미 다른 계정에서 사용 중인 이메일입니다.", HttpStatus.CONFLICT),
	EMAIL_REQUIRED("USER-010", "이메일 제공에 동의해야 가입할 수 있습니다.", HttpStatus.BAD_REQUEST),

	// 회사 이메일 인증(직장 인증)
	VERIFICATION_NOT_FOUND("USER-003", "인증 요청 내역이 없습니다. 인증번호를 다시 요청해 주세요.", HttpStatus.NOT_FOUND),
	VERIFICATION_EXPIRED("USER-004", "만료된 인증번호입니다. 다시 요청해 주세요.", HttpStatus.GONE),
	VERIFICATION_ALREADY_VERIFIED("USER-005", "이미 인증이 완료되었습니다.", HttpStatus.CONFLICT),
	VERIFICATION_CODE_MISMATCH("USER-006", "인증번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
	PERSONAL_EMAIL_NOT_ALLOWED("USER-008", "개인 이메일로는 직장 인증을 할 수 없습니다. 회사 이메일을 입력해 주세요.", HttpStatus.BAD_REQUEST),
}
