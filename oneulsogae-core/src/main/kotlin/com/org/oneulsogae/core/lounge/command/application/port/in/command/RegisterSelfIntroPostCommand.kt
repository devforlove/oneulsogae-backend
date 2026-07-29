package com.org.oneulsogae.core.lounge.command.application.port.`in`.command

/**
 * 셀프 소개팅(셀소) 등록 명령. 컨트롤러(api)가 MultipartFile·폼 필드에서 값을 뽑아 넘긴다.
 * (core는 웹 타입에 의존하지 않도록 원시 바이트·메타만 받는다)
 */
data class RegisterSelfIntroPostCommand(
	/** 첨부한 사진. 목록 순서가 곧 노출 순서다. */
	val photos: List<FilePart>,
	/** 관심사. */
	val interests: String,
	/** 이상형/연애관. */
	val idealType: String,
	/** 나의 매력 어필. */
	val charmPoint: String,
) {

	/** 업로드 파일 한 개의 바이트·메타. */
	data class FilePart(
		val content: ByteArray,
		val contentType: String?,
		val size: Long,
	)
}
