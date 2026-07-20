package com.org.oneulsogae.core.gathering

import com.org.oneulsogae.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 유저용 모임(gathering) 도메인 에러 코드.
 * [com.org.oneulsogae.core.common.error.BusinessException]에 넘겨 사용한다.
 * (어드민 모듈의 `AdminErrorCode`(GATHER-*)와는 별개 네임스페이스다)
 */
enum class GatheringErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	/** 모집중 모임을 id로 찾지 못함(없거나 모집중이 아님). */
	GATHERING_NOT_FOUND("GATHERING-001", "모임을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 참가 신청 대상 일정을 찾지 못함(없거나 요청 모임 소속이 아님). */
	GATHERING_SCHEDULE_NOT_FOUND("GATHERING-002", "모임 일정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 판매 중(예정 상태)이 아닌 일정에 참가 신청함. */
	GATHERING_SCHEDULE_NOT_OPEN("GATHERING-003", "참가 신청할 수 없는 일정입니다.", HttpStatus.CONFLICT),

	/** 해당 성별 정원이 모두 찼음(승인대기 포함). */
	GATHERING_SOLD_OUT("GATHERING-004", "정원이 마감되었습니다.", HttpStatus.CONFLICT),

	/** 같은 일정에 이미 승인대기 또는 참가 상태의 신청이 있음. */
	GATHERING_ALREADY_JOINED("GATHERING-005", "이미 참가 신청한 일정입니다.", HttpStatus.CONFLICT),

	/** 상품을 id로 찾지 못함(없거나 삭제됨). */
	GATHERING_PRODUCT_NOT_FOUND("GATHERING-006", "상품을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 얼리버드 상품(EARLY_BIRD)으로 접수를 시도했으나 얼리버드가 소진됨(체크아웃 이후 소진된 경우). */
	GATHERING_EARLY_BIRD_SOLD_OUT("GATHERING-007", "얼리버드가 마감되었습니다. 최신 금액으로 다시 시도해주세요.", HttpStatus.CONFLICT),

	// 멤버 인증(member_verifications)
	/** 얼굴·신분증 사진이 허용 형식(JPEG·PNG)이 아님. */
	MEMBER_VERIFICATION_INVALID_PHOTO_TYPE("GATHERING-008", "지원하지 않는 사진 형식입니다. JPEG·PNG만 업로드할 수 있습니다.", HttpStatus.BAD_REQUEST),

	/** 직장 인증 서류가 허용 형식(JPEG·PNG·PDF)이 아님. */
	MEMBER_VERIFICATION_INVALID_DOCUMENT_TYPE("GATHERING-009", "지원하지 않는 파일 형식입니다. JPEG·PNG·PDF만 업로드할 수 있습니다.", HttpStatus.BAD_REQUEST),

	/** 업로드 파일이 비어 있음. */
	MEMBER_VERIFICATION_EMPTY_FILE("GATHERING-010", "파일이 비어 있습니다.", HttpStatus.BAD_REQUEST),

	/** 업로드 파일이 최대 크기(10MB)를 초과함. */
	MEMBER_VERIFICATION_FILE_TOO_LARGE("GATHERING-011", "파일이 너무 큽니다. 최대 10MB까지 업로드할 수 있습니다.", HttpStatus.PAYLOAD_TOO_LARGE),

	/** 직업 정보(직종·직장명/직종/직급)가 공백이거나 최대 길이를 초과함. */
	MEMBER_VERIFICATION_INVALID_JOB_INFO("GATHERING-012", "직업 정보를 입력해 주세요. (직종 최대 30자, 직장명/직종/직급 최대 100자)", HttpStatus.BAD_REQUEST),
}
