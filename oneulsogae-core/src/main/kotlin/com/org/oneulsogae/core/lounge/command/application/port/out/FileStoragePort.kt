package com.org.oneulsogae.core.lounge.command.application.port.out

/**
 * 라운지 사진의 파일 스토리지(S3) 저장 out-port. 인프라 세부(S3 SDK)를 감춘다.
 * 파일은 비공개로 저장하고 DB에는 반환된 오브젝트 키만 보관한다. (열람용 URL은 조회 시 presigned로 발급)
 */
fun interface FileStoragePort {

	/**
	 * [content]를 [key] 경로에 [contentType]으로 저장한다.
	 * @return 저장에 사용된 오브젝트 키. (호출부가 넘긴 [key]와 동일 — DB 보관용)
	 */
	fun upload(key: String, content: ByteArray, contentType: String): String
}
