package com.org.oneulsogae.admin.companyverification.query.service.port.out

/**
 * 서류 이미지의 열람용 URL 발급 out-port. (S3 presigned GET URL)
 * admin은 인터페이스만 소유하고, infra가 S3Presigner로 구현한다.
 */
fun interface CompanyVerificationImageUrlPort {

	/** [imageKey](S3 오브젝트 키)에 대한, 일정 시간 유효한 열람용 URL을 발급한다. */
	fun presignedGetUrl(imageKey: String): String
}
