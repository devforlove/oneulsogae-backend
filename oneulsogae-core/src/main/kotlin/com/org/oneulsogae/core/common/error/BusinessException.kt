package com.org.oneulsogae.core.common.error

/**
 * 에러 코드(enum)를 담는 커스텀 예외.
 * [ErrorCode]를 구현한 enum을 넘겨 던지면, 표현 계층의 핸들러가
 * 해당 코드에 맞는 에러 응답(상태 코드 + 본문)을 내려준다.
 *
 * 기본 메시지는 [ErrorCode.message]를 따르되, 필요하면 상세 메시지로 덮어쓸 수 있다.
 */
open class BusinessException(
	val errorCode: ErrorCode,
	override val message: String = errorCode.message,
) : RuntimeException(message)
