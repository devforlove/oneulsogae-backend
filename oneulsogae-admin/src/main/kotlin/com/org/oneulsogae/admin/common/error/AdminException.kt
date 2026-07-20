package com.org.oneulsogae.admin.common.error

/**
 * 어드민 도메인 커스텀 예외. [AdminErrorCode]를 담아 던지면
 * [AdminExceptionHandler]가 코드에 맞는 에러 응답(상태 코드 + 본문)으로 변환한다.
 */
class AdminException(
	val errorCode: AdminErrorCode,
	override val message: String = errorCode.message,
) : RuntimeException(message)
