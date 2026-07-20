package com.org.oneulsogae.core.user.command.application.port.out

/**
 * 파일 스토리지(S3) 저장 out-port. 인프라 세부(S3 SDK)를 감춘다.
 * 서류 이미지처럼 공개되면 안 되는 파일을 비공개로 저장하고, DB에는 반환된 오브젝트 키만 보관한다.
 */
fun interface FileStoragePort {

	/**
	 * [content]를 [key] 경로에 [contentType]으로 저장한다.
	 * @return 저장에 사용된 오브젝트 키. (호출부가 넘긴 [key]와 동일 — DB 보관용)
	 */
	fun upload(key: String, content: ByteArray, contentType: String): String
}
