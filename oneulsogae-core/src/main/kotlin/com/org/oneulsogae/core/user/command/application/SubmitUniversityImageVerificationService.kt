package com.org.oneulsogae.core.user.command.application

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.command.application.port.`in`.SubmitUniversityImageVerificationUseCase
import com.org.oneulsogae.core.user.command.application.port.`in`.command.SubmitUniversityImageVerificationCommand
import com.org.oneulsogae.core.user.command.application.port.out.FileStoragePort
import com.org.oneulsogae.core.user.command.application.port.out.GetUserDetailPort
import com.org.oneulsogae.core.user.command.application.port.out.GetUserPort
import com.org.oneulsogae.core.user.command.application.port.out.SaveUniversityImageVerificationPort
import com.org.oneulsogae.core.user.command.domain.UniversityImageVerification
import com.org.oneulsogae.core.user.command.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * [SubmitUniversityImageVerificationUseCase] 구현. (직장 서류 인증 제출과 같은 흐름)
 * 업로드한 서류 파일을 검증([UniversityImageVerification.validateUpload])한 뒤 S3에 비공개로 올리고([FileStoragePort]),
 * 그 오브젝트 키와 심사 상태(PENDING)를 university_image_verifications에 저장한다.
 * 자동 검증이 불가능한 서류이므로 이 시점에 프로필은 바꾸지 않는다. (승인/반려는 어드민 심사)
 */
@Service
class SubmitUniversityImageVerificationService(
	private val getUserPort: GetUserPort,
	private val getUserDetailPort: GetUserDetailPort,
	private val fileStoragePort: FileStoragePort,
	private val saveUniversityImageVerificationPort: SaveUniversityImageVerificationPort,
) : SubmitUniversityImageVerificationUseCase {

	@Transactional
	override fun submit(userId: Long, command: SubmitUniversityImageVerificationCommand): UniversityImageVerification {
		val user: User = getUserPort.findById(userId)
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다: $userId")

		// 잘못된 입력이 S3에 올라가지 않도록 업로드 전에 파일·학교명을 모두 검증한다. (검증 실패로 롤백돼도 S3 고아 객체가 남지 않게)
		UniversityImageVerification.validateUpload(command.contentType, command.size)
		UniversityImageVerification.validateUniversityName(command.universityName)
		val contentType: String = command.contentType!!

		// 승인 시 프로필 학교명이 덮어써지므로, 제출 시점의 프로필 학교명을 이전 학교명으로 스냅샷해 심사 상세에서 보여준다.
		val previousUniversityName: String? = getUserDetailPort.findByUserId(user.id)?.universityName

		val key: String = objectKey(user.id, contentType)
		fileStoragePort.upload(key, command.content, contentType)

		return saveUniversityImageVerificationPort.save(
			UniversityImageVerification.create(
				userId = user.id,
				imageKey = key,
				universityName = command.universityName,
				previousUniversityName = previousUniversityName,
			),
		)
	}

	/** 사용자별 폴더 아래 충돌 없는 오브젝트 키를 만든다. (예: university-image-verifications/42/{uuid}.jpg) */
	private fun objectKey(userId: Long, contentType: String): String {
		val extension: String = UniversityImageVerification.extensionOf(contentType)
		return "$KEY_PREFIX/$userId/${UUID.randomUUID()}.$extension"
	}

	companion object {
		private const val KEY_PREFIX: String = "university-image-verifications"
	}
}
