package com.org.oneulsogae.admin.gathering.command.application.port.out

/**
 * 모임 대표 이미지 저장 out-port. (S3 등 파일 스토리지)
 * admin은 인터페이스만 소유하고, infra가 S3Client로 구현한다.
 */
fun interface UploadGatheringImagePort {

	/**
	 * [content]를 [key] 경로에 [contentType]으로 저장한다.
	 * @return 저장에 사용된 오브젝트 키. (호출부가 넘긴 [key]와 동일 — DB 보관용)
	 */
	fun upload(key: String, content: ByteArray, contentType: String): String
}
