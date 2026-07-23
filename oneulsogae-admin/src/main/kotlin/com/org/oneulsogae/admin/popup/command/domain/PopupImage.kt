package com.org.oneulsogae.admin.popup.command.domain

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException

/**
 * 팝업 이미지 업로드 규칙. 표시용 이미지이므로 JPEG·PNG만 허용한다.
 * 잘못된 파일이 S3에 올라가지 않도록, 업로드 전 [validate]로 형식·크기를 검증한다.
 * 공개 프록시(/images/{key})가 이 프리픽스로 시작하는 key만 서빙하도록 [isValidKey]가 화이트리스트에 쓰인다.
 */
object PopupImage {

	/** 팝업 이미지 오브젝트 키 프리픽스. */
	const val KEY_PREFIX: String = "popups"

	/** 허용하는 이미지 콘텐츠 타입. */
	val ALLOWED_CONTENT_TYPES: Set<String> = setOf("image/jpeg", "image/png")

	/** 허용하는 최대 파일 크기(바이트). 10MB. */
	const val MAX_FILE_SIZE_BYTES: Long = 10L * 1024 * 1024

	/** 업로드 파일의 형식·크기를 검증한다. 비어 있거나 허용하지 않는 형식이면 POPUP_INVALID_IMAGE_TYPE, 너무 크면 POPUP_IMAGE_TOO_LARGE. */
	fun validate(contentType: String?, size: Long) {
		if (size <= 0 || contentType == null || contentType.lowercase() !in ALLOWED_CONTENT_TYPES) {
			throw AdminException(AdminErrorCode.POPUP_INVALID_IMAGE_TYPE)
		}
		if (size > MAX_FILE_SIZE_BYTES) {
			throw AdminException(AdminErrorCode.POPUP_IMAGE_TOO_LARGE)
		}
	}

	/** 콘텐츠 타입에 대응하는 파일 확장자. (오브젝트 키 생성용) */
	fun extensionOf(contentType: String): String =
		when (contentType.lowercase()) {
			"image/jpeg" -> "jpg"
			"image/png" -> "png"
			else -> throw AdminException(AdminErrorCode.POPUP_INVALID_IMAGE_TYPE)
		}

	/** [key]가 팝업 프리픽스로 시작하고 경로 조작(`..`, `\`)이 없는 유효한 이미지 키인지 검사한다. */
	fun isValidKey(key: String): Boolean =
		key.startsWith("$KEY_PREFIX/") && !key.contains("..") && !key.contains('\\')
}
