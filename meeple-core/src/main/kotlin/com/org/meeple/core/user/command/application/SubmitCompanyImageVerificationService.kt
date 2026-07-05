package com.org.meeple.core.user.command.application

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.user.command.application.port.`in`.SubmitCompanyImageVerificationUseCase
import com.org.meeple.core.user.command.application.port.`in`.command.SubmitCompanyImageVerificationCommand
import com.org.meeple.core.user.command.application.port.out.FileStoragePort
import com.org.meeple.core.user.command.application.port.out.GetUserPort
import com.org.meeple.core.user.command.application.port.out.SaveCompanyImageVerificationPort
import com.org.meeple.core.user.command.domain.CompanyImageVerification
import com.org.meeple.core.user.command.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * [SubmitCompanyImageVerificationUseCase] 구현.
 * 업로드한 서류 파일을 검증([CompanyImageVerification.validateUpload])한 뒤 S3에 비공개로 올리고([FileStoragePort]),
 * 그 오브젝트 키와 심사 상태(PENDING)를 company_image_verifications에 저장한다.
 * 자동 검증이 불가능한 서류이므로 이 시점에 가입 상태는 바꾸지 않는다. (승인/반려는 어드민 심사 — 이번 범위 밖)
 */
@Service
class SubmitCompanyImageVerificationService(
	private val getUserPort: GetUserPort,
	private val fileStoragePort: FileStoragePort,
	private val saveCompanyImageVerificationPort: SaveCompanyImageVerificationPort,
) : SubmitCompanyImageVerificationUseCase {

	@Transactional
	override fun submit(userId: Long, command: SubmitCompanyImageVerificationCommand): CompanyImageVerification {
		val user: User = getUserPort.findById(userId)
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다: $userId")

		CompanyImageVerification.validateUpload(command.contentType, command.size)
		val contentType: String = command.contentType!!

		val key: String = objectKey(user.id, contentType)
		fileStoragePort.upload(key, command.content, contentType)

		return saveCompanyImageVerificationPort.save(
			CompanyImageVerification.create(userId = user.id, imageKey = key, companyName = command.companyName),
		)
	}

	/** 사용자별 폴더 아래 충돌 없는 오브젝트 키를 만든다. (예: company-image-verifications/42/{uuid}.jpg) */
	private fun objectKey(userId: Long, contentType: String): String {
		val extension: String = CompanyImageVerification.extensionOf(contentType)
		return "$KEY_PREFIX/$userId/${UUID.randomUUID()}.$extension"
	}

	companion object {
		private const val KEY_PREFIX: String = "company-image-verifications"
	}
}
