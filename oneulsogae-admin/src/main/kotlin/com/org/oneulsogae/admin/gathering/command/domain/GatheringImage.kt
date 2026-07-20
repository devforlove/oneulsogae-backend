package com.org.oneulsogae.admin.gathering.command.domain

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException

/**
 * 모임 대표 이미지 업로드 규칙. 표시용 이미지이므로 JPEG·PNG만 허용한다.
 * 잘못된 파일이 S3에 올라가지 않도록, 업로드 전 [validate]로 형식·크기를 검증한다.
 */
object GatheringImage {

	/** 허용하는 이미지 콘텐츠 타입. */
	val ALLOWED_CONTENT_TYPES: Set<String> = setOf("image/jpeg", "image/png")

	/** 허용하는 최대 파일 크기(바이트). 10MB. */
	const val MAX_FILE_SIZE_BYTES: Long = 10L * 1024 * 1024

	/** 업로드 파일의 형식·크기를 검증한다. 비어 있거나 허용하지 않는 형식이면 INVALID_IMAGE_TYPE, 너무 크면 IMAGE_TOO_LARGE. */
	fun validate(contentType: String?, size: Long) {
		if (size <= 0 || contentType == null || contentType.lowercase() !in ALLOWED_CONTENT_TYPES) {
			throw AdminException(AdminErrorCode.GATHERING_INVALID_IMAGE_TYPE)
		}
		if (size > MAX_FILE_SIZE_BYTES) {
			throw AdminException(AdminErrorCode.GATHERING_IMAGE_TOO_LARGE)
		}
	}

	/** 콘텐츠 타입에 대응하는 파일 확장자. (오브젝트 키 생성용) */
	fun extensionOf(contentType: String): String =
		when (contentType.lowercase()) {
			"image/jpeg" -> "jpg"
			"image/png" -> "png"
			else -> throw AdminException(AdminErrorCode.GATHERING_INVALID_IMAGE_TYPE)
		}
}
