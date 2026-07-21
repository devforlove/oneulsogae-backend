package com.org.oneulsogae.core.lounge

import com.org.oneulsogae.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 라운지(lounge) 도메인 에러 코드.
 * [com.org.oneulsogae.core.common.error.BusinessException]에 넘겨 사용한다.
 */
enum class LoungeErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	// 셀프 소개팅(셀소) 등록
	/** 사진을 한 장도 첨부하지 않음. */
	SELF_INTRO_PHOTO_REQUIRED("LOUNGE-001", "사진을 최소 1장 등록해주세요.", HttpStatus.BAD_REQUEST),

	/** 사진을 허용 장수보다 많이 첨부함. */
	SELF_INTRO_TOO_MANY_PHOTOS("LOUNGE-002", "사진은 최대 5장까지 등록할 수 있습니다.", HttpStatus.BAD_REQUEST),

	/** 첨부한 사진 파일이 비어 있음. */
	SELF_INTRO_EMPTY_PHOTO("LOUNGE-003", "빈 파일은 업로드할 수 없습니다.", HttpStatus.BAD_REQUEST),

	/** 사진이 허용 형식(JPEG·PNG)이 아님. */
	SELF_INTRO_INVALID_PHOTO_TYPE("LOUNGE-004", "지원하지 않는 사진 형식입니다. JPEG·PNG만 업로드할 수 있습니다.", HttpStatus.BAD_REQUEST),

	/** 사진 크기가 허용 범위를 넘음. */
	SELF_INTRO_PHOTO_TOO_LARGE("LOUNGE-005", "사진 크기가 너무 큽니다. 장당 10MB까지 업로드할 수 있습니다.", HttpStatus.BAD_REQUEST),

	/** 본문 항목이 비었거나 최대 길이를 넘음. */
	SELF_INTRO_INVALID_CONTENT("LOUNGE-006", "셀소 내용을 형식에 맞게 모두 입력해주세요.", HttpStatus.BAD_REQUEST),

	/** 최근 24시간 안에 이미 셀소를 등록함. */
	SELF_INTRO_DAILY_LIMIT_EXCEEDED("LOUNGE-007", "셀소는 하루에 한 번만 등록할 수 있습니다.", HttpStatus.TOO_MANY_REQUESTS),

	// 셀프 소개팅 조회
	/** 셀소를 id로 찾지 못함(없거나 삭제됨). */
	SELF_INTRO_POST_NOT_FOUND("LOUNGE-008", "셀소를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	// 라운지 대화 신청
	/** 본인이 작성한 셀소에 대화를 신청함. */
	LOUNGE_CHAT_REQUEST_SELF("LOUNGE-009", "본인이 작성한 글에는 대화를 신청할 수 없습니다.", HttpStatus.BAD_REQUEST),

	/** 같은 글에 이미 대화를 신청함. */
	LOUNGE_CHAT_REQUEST_DUPLICATED("LOUNGE-010", "이미 대화를 신청한 글입니다.", HttpStatus.CONFLICT),

	/** 본인이 작성한 글이 아님. (신청 목록 조회·수락) */
	LOUNGE_POST_NOT_OWNED("LOUNGE-011", "본인이 작성한 글이 아닙니다.", HttpStatus.FORBIDDEN),

	/** 대화 신청을 id로 찾지 못함(없거나 삭제됨). */
	LOUNGE_CHAT_REQUEST_NOT_FOUND("LOUNGE-012", "대화 신청을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 이미 수락한 대화 신청. */
	LOUNGE_CHAT_REQUEST_ALREADY_ACCEPTED("LOUNGE-013", "이미 수락한 대화 신청입니다.", HttpStatus.CONFLICT),
}
