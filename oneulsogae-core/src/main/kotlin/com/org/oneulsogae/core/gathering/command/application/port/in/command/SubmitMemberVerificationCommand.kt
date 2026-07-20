package com.org.oneulsogae.core.gathering.command.application.port.`in`.command

/**
 * 멤버 인증(본인인증) 제출 명령. 컨트롤러(api)가 MultipartFile·폼 필드에서 값을 뽑아 넘긴다.
 * (core는 웹 타입에 의존하지 않도록 원시 바이트·메타만 받는다)
 */
data class SubmitMemberVerificationCommand(
	/** 얼굴 사진. */
	val face: FilePart,
	/** 신분증 사진. (주민등록증·운전면허증·여권 등 — 연령 인증 및 본인 확인용) */
	val idCard: FilePart,
	/** 직장 인증 서류(공무원증·사원증·학생증 등). */
	val document: FilePart,
	/** 직종. */
	val jobCategory: String,
	/** 직장명/직종/직급 상세. */
	val jobDetail: String,
) {

	/** 업로드 파일 한 개의 바이트·메타. */
	data class FilePart(
		val content: ByteArray,
		val contentType: String?,
		val size: Long,
	)
}
