package com.org.meeple.core.user.command.domain

import com.org.meeple.common.user.MemberVerificationStatus
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode

/**
 * 멤버 인증(본인인증) 도메인 모델.
 * 사용자가 직업 정보(직종·직장명/직종/직급)와 사진 3종(얼굴·전신·직장 인증 서류)을 제출하면,
 * 각 파일의 S3 오브젝트 키와 심사 상태([status])를 보관한다. 자동 검증이 불가능해 제출 시
 * [MemberVerificationStatus.PENDING]으로 시작하며, 어드민 심사로 승인/반려된다. (어드민 심사는 이번 범위 밖 — 상태 필드로 확장 여지를 둔다)
 */
data class MemberVerification(
	val id: Long = 0,
	val userId: Long,
	/** 직종. (예: "IT·개발직", "공무원" — 프론트 목록의 자유 문자열) */
	val jobCategory: String,
	/** 직장명/직종/직급 상세. (어드민 심사 근거) */
	val jobDetail: String,
	/** 얼굴 사진의 S3 오브젝트 키. (버킷은 설정에서 관리 — 공개 URL이 아니라 키만 보관해 노출을 막는다) */
	val faceImageKey: String,
	/** 전신 사진의 S3 오브젝트 키. */
	val bodyImageKey: String,
	/** 직장 인증 서류(공무원증·사원증·학생증 등)의 S3 오브젝트 키. */
	val documentImageKey: String,
	val status: MemberVerificationStatus = MemberVerificationStatus.PENDING,
	/** 어드민 반려 사유. 반려 시에만 채워진다. */
	val rejectionReason: String? = null,
) {

	init {
		require(faceImageKey.isNotBlank()) { "faceImageKey must not be blank" }
		require(bodyImageKey.isNotBlank()) { "bodyImageKey must not be blank" }
		require(documentImageKey.isNotBlank()) { "documentImageKey must not be blank" }
	}

	companion object {

		/** 허용하는 사진(얼굴·전신) 콘텐츠 타입. (JPEG·PNG) */
		val PHOTO_CONTENT_TYPES: Set<String> = setOf("image/jpeg", "image/png")

		/** 허용하는 서류 콘텐츠 타입. (JPEG·PNG·PDF) */
		val DOCUMENT_CONTENT_TYPES: Set<String> = setOf("image/jpeg", "image/png", "application/pdf")

		/** 허용하는 최대 파일 크기(바이트). 10MB. */
		const val MAX_FILE_SIZE_BYTES: Long = 10L * 1024 * 1024

		/** 직종의 최대 길이. */
		const val MAX_JOB_CATEGORY_LENGTH: Int = 30

		/** 직장명/직종/직급 상세의 최대 길이. */
		const val MAX_JOB_DETAIL_LENGTH: Int = 100

		/** 신규 제출(심사 대기)을 생성한다. 직업 정보를 검증해 담는다. */
		fun create(
			userId: Long,
			jobCategory: String,
			jobDetail: String,
			faceImageKey: String,
			bodyImageKey: String,
			documentImageKey: String,
		): MemberVerification {
			validateJobInfo(jobCategory, jobDetail)
			return MemberVerification(
				userId = userId,
				jobCategory = jobCategory,
				jobDetail = jobDetail,
				faceImageKey = faceImageKey,
				bodyImageKey = bodyImageKey,
				documentImageKey = documentImageKey,
			)
		}

		/**
		 * 직업 정보 검증. 직종([jobCategory])·직장명/직종/직급([jobDetail])이 공백이거나
		 * 각 최대 길이를 넘으면 [UserErrorCode.INVALID_JOB_INFO].
		 */
		fun validateJobInfo(jobCategory: String, jobDetail: String) {
			if (jobCategory.isBlank() || jobCategory.length > MAX_JOB_CATEGORY_LENGTH) {
				throw BusinessException(UserErrorCode.INVALID_JOB_INFO)
			}
			if (jobDetail.isBlank() || jobDetail.length > MAX_JOB_DETAIL_LENGTH) {
				throw BusinessException(UserErrorCode.INVALID_JOB_INFO)
			}
		}

		/**
		 * 업로드한 사진(얼굴·전신) 파일이 멤버 인증에 쓸 수 있는지 검증한다. (업로드 전에 호출해 잘못된 파일이 S3에 올라가지 않게 한다)
		 * - 빈 파일: [UserErrorCode.EMPTY_IMAGE]
		 * - 허용하지 않는 형식: [UserErrorCode.INVALID_MEMBER_PHOTO_TYPE]
		 * - 크기 초과: [UserErrorCode.IMAGE_TOO_LARGE]
		 */
		fun validatePhoto(contentType: String?, size: Long) {
			validateUpload(contentType, size, PHOTO_CONTENT_TYPES, UserErrorCode.INVALID_MEMBER_PHOTO_TYPE)
		}

		/**
		 * 업로드한 서류 파일이 멤버 인증에 쓸 수 있는지 검증한다.
		 * - 빈 파일: [UserErrorCode.EMPTY_IMAGE]
		 * - 허용하지 않는 형식: [UserErrorCode.INVALID_IMAGE_TYPE]
		 * - 크기 초과: [UserErrorCode.IMAGE_TOO_LARGE]
		 */
		fun validateDocument(contentType: String?, size: Long) {
			validateUpload(contentType, size, DOCUMENT_CONTENT_TYPES, UserErrorCode.INVALID_IMAGE_TYPE)
		}

		private fun validateUpload(
			contentType: String?,
			size: Long,
			allowedContentTypes: Set<String>,
			invalidTypeErrorCode: UserErrorCode,
		) {
			if (size <= 0L) {
				throw BusinessException(UserErrorCode.EMPTY_IMAGE)
			}
			if (contentType == null || contentType.lowercase() !in allowedContentTypes) {
				throw BusinessException(invalidTypeErrorCode)
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
