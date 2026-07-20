package com.org.oneulsogae.core.lounge.query.service.port.out

/**
 * 라운지 사진의 열람용 URL 발급 out-port. (S3 presigned GET URL)
 * core는 인터페이스만 소유하고, infra가 S3Presigner로 구현한다.
 */
fun interface LoungeImageUrlPort {

	/** [imageKey](S3 오브젝트 키)에 대한, 일정 시간 유효한 열람용 URL을 발급한다. */
	fun presignedGetUrl(imageKey: String): String
}
