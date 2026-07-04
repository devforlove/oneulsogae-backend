package com.org.meeple.core.user.command.application.port.`in`.command

/**
 * 직장 서류 이미지 인증 제출 명령. 컨트롤러(api)가 MultipartFile에서 값을 뽑아 넘긴다.
 * (core는 웹 타입에 의존하지 않도록 원시 바이트·메타만 받는다)
 */
data class SubmitCompanyImageVerificationCommand(
	val content: ByteArray,
	val contentType: String?,
	val size: Long,
)
