package com.org.meeple.core.user.command.domain

import com.org.meeple.common.user.CompanyImageVerificationStatus
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode

/**
 * 직장 서류 이미지 인증 도메인 모델.
 * 사용자가 재직증명서 등 직장 서류 이미지를 업로드하면, 그 파일의 S3 오브젝트 키([imageKey])와
 * 심사 상태([status])를 보관한다. 서류는 자동 검증이 불가능해 제출 시 [CompanyImageVerificationStatus.PENDING]으로
 * 시작하며, 어드민 심사로 승인/반려된다. (승인 처리 및 가입 상태 반영은 이번 범위 밖 — 상태 필드로 확장 여지를 둔다)
 */
data class CompanyImageVerification(
	val id: Long = 0,
	val userId: Long,
	/** 업로드한 서류 파일의 S3 오브젝트 키. (버킷은 설정에서 관리 — 공개 URL이 아니라 키만 보관해 노출을 막는다) */
	val imageKey: String,
	val status: CompanyImageVerificationStatus = CompanyImageVerificationStatus.PENDING,
) {

	init {
		require(imageKey.isNotBlank()) { "imageKey must not be blank" }
	}

	companion object {

		/** 허용하는 서류 이미지 콘텐츠 타입. (JPEG·PNG·PDF) */
		val ALLOWED_CONTENT_TYPES: Set<String> = setOf("image/jpeg", "image/png", "application/pdf")

		/** 허용하는 최대 파일 크기(바이트). 10MB. */
		const val MAX_FILE_SIZE_BYTES: Long = 10L * 1024 * 1024

		/** 신규 제출(심사 대기)을 생성한다. */
		fun create(userId: Long, imageKey: String): CompanyImageVerification =
			CompanyImageVerification(userId = userId, imageKey = imageKey)

		/**
		 * 업로드한 파일이 서류 인증에 쓸 수 있는지 검증한다. (업로드 전에 호출해 잘못된 파일이 S3에 올라가지 않게 한다)
		 * - 빈 파일: [UserErrorCode.EMPTY_IMAGE]
		 * - 허용하지 않는 형식: [UserErrorCode.INVALID_IMAGE_TYPE]
		 * - 크기 초과: [UserErrorCode.IMAGE_TOO_LARGE]
		 */
		fun validateUpload(contentType: String?, size: Long) {
			if (size <= 0L) {
				throw BusinessException(UserErrorCode.EMPTY_IMAGE)
			}
			if (contentType == null || contentType.lowercase() !in ALLOWED_CONTENT_TYPES) {
				throw BusinessException(UserErrorCode.INVALID_IMAGE_TYPE)
			}
			if (size > MAX_FILE_SIZE_BYTES) {
				throw BusinessException(UserErrorCode.IMAGE_TOO_LARGE)
			}
		}

		/** 콘텐츠 타입에 대응하는 파일 확장자. (오브젝트 키 생성에 쓴다) */
		fun extensionOf(contentType: String): String =
			when (contentType.lowercase()) {
				"image/jpeg" -> "jpg"
				"image/png" -> "png"
				"application/pdf" -> "pdf"
				else -> "bin"
			}
	}
}
