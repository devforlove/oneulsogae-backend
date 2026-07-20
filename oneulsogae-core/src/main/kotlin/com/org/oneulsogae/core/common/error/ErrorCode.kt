package com.org.oneulsogae.core.common.error

import org.springframework.http.HttpStatus

/**
 * 에러 코드 계약.
 * 각 도메인은 이 인터페이스를 구현하는 enum으로 자신의 에러를 정의하고,
 * 해당 enum을 [BusinessException]에 넘겨 일관된 에러 응답이 내려가도록 한다.
 */
interface ErrorCode {

	/** 비즈니스 에러 식별 코드. (예: "USER-001") */
	val code: String

	/** 클라이언트에 전달할 기본 메시지. */
	val message: String

	/** 내려줄 HTTP 상태 코드. */
	val status: HttpStatus
}
