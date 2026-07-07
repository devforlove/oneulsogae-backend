package com.org.meeple.admin.gathering.command.application.port.`in`

/** 모임 소개(Markdown)에 삽입할 이미지를 업로드하는 유스케이스. */
fun interface UploadGatheringDescriptionImageUseCase {

	/** [content]를 검증(JPEG/PNG·5MB)한 뒤 S3에 저장하고 오브젝트 key를 반환한다. */
	fun execute(content: ByteArray?, contentType: String?, size: Long): String
}
