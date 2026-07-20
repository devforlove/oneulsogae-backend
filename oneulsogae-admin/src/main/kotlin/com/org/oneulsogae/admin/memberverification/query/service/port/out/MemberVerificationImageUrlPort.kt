package com.org.oneulsogae.admin.memberverification.query.service.port.out

/**
 * 멤버 인증 사진(얼굴·신분증·서류)의 열람용 URL 발급 out-port. (S3 presigned GET URL)
 * admin은 인터페이스만 소유하고, infra가 S3Presigner로 구현한다.
 */
fun interface MemberVerificationImageUrlPort {

	/** [imageKey](S3 오브젝트 키)에 대한, 일정 시간 유효한 열람용 URL을 발급한다. */
	fun presignedGetUrl(imageKey: String): String
}
