package com.org.meeple.core.user

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
	BIRTHDAY_REQUIRED("USER-014", "생년월일을 입력해 주세요.", HttpStatus.BAD_REQUEST),
	INVALID_BIRTHDAY("USER-015", "생년월일이 올바르지 않습니다. 만 19세 이상 100세 이하만 가입할 수 있습니다.", HttpStatus.BAD_REQUEST),
	EMAIL_ALREADY_REGISTERED("USER-009", "이미 다른 계정에서 사용 중인 이메일입니다.", HttpStatus.CONFLICT),
	EMAIL_REQUIRED("USER-010", "이메일 제공에 동의해야 가입할 수 있습니다.", HttpStatus.BAD_REQUEST),

	// 이상형(user_ideal_types)
	INVALID_IDEAL_TYPE_RANGE("USER-019", "이상형 범위가 올바르지 않습니다. 최소값이 최대값보다 클 수 없고, 한쪽만 입력할 수 없습니다.", HttpStatus.BAD_REQUEST),

	// 회사 이메일 인증(직장 인증)
	VERIFICATION_NOT_FOUND("USER-003", "인증 요청 내역이 없습니다. 인증번호를 다시 요청해 주세요.", HttpStatus.NOT_FOUND),
	VERIFICATION_EXPIRED("USER-004", "만료된 인증번호입니다. 다시 요청해 주세요.", HttpStatus.GONE),
	VERIFICATION_ALREADY_VERIFIED("USER-005", "이미 인증이 완료되었습니다.", HttpStatus.CONFLICT),
	VERIFICATION_CODE_MISMATCH("USER-006", "인증번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
	PERSONAL_EMAIL_NOT_ALLOWED("USER-008", "개인 이메일로는 직장 인증을 할 수 없습니다. 회사 이메일을 입력해 주세요.", HttpStatus.BAD_REQUEST),
	COMPANY_EMAIL_ALREADY_USED("USER-017", "이미 다른 사용자가 인증한 회사 이메일입니다.", HttpStatus.CONFLICT),

	// 학교 이메일 인증(대학 인증)
	UNIVERSITY_NOT_FOUND("USER-016", "확인되지 않는 학교 이메일입니다. 본인 학교의 이메일을 입력해 주세요.", HttpStatus.BAD_REQUEST),
	UNIVERSITY_EMAIL_ALREADY_USED("USER-018", "이미 다른 사용자가 인증한 학교 이메일입니다.", HttpStatus.CONFLICT),

	// 직장 서류 이미지 인증(company_image_verifications)
	EMPTY_IMAGE("USER-020", "이미지 파일이 비어 있습니다.", HttpStatus.BAD_REQUEST),
	INVALID_IMAGE_TYPE("USER-021", "지원하지 않는 파일 형식입니다. JPEG·PNG·PDF만 업로드할 수 있습니다.", HttpStatus.BAD_REQUEST),
	IMAGE_TOO_LARGE("USER-022", "파일이 너무 큽니다. 최대 10MB까지 업로드할 수 있습니다.", HttpStatus.PAYLOAD_TOO_LARGE),
	INVALID_COMPANY_NAME("USER-023", "회사명을 입력해 주세요. (최대 50자)", HttpStatus.BAD_REQUEST),

	// 본인확인(KCP identity_verification)
	KCP_REGISTER_FAILED("USER-024", "본인확인 거래등록에 실패했습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.BAD_GATEWAY),
	KCP_QUERY_FAILED("USER-025", "본인확인 결과 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.BAD_GATEWAY),
	IDENTITY_VERIFICATION_NOT_FOUND("USER-026", "본인확인 요청 내역이 없습니다. 처음부터 다시 시도해 주세요.", HttpStatus.NOT_FOUND),
	IDENTITY_VERIFICATION_MISMATCH("USER-027", "본인확인 거래 정보가 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
	IDENTITY_ALREADY_VERIFIED("USER-028", "이미 완료된 본인확인 요청입니다.", HttpStatus.CONFLICT),
	IDENTITY_NOT_ADULT("USER-029", "만 19세 이상만 가입할 수 있습니다.", HttpStatus.BAD_REQUEST),
	IDENTITY_ALREADY_REGISTERED("USER-030", "이미 본인확인으로 가입된 사용자입니다.", HttpStatus.CONFLICT),
	IDENTITY_VERIFICATION_REQUIRED("USER-031", "본인확인을 먼저 완료해 주세요.", HttpStatus.BAD_REQUEST),
}
