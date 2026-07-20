package com.org.oneulsogae.core.user.command.application

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.command.application.port.`in`.SubmitCompanyImageVerificationUseCase
import com.org.oneulsogae.core.user.command.application.port.`in`.command.SubmitCompanyImageVerificationCommand
import com.org.oneulsogae.core.user.command.application.port.out.FileStoragePort
import com.org.oneulsogae.core.user.command.application.port.out.GetUserDetailPort
import com.org.oneulsogae.core.user.command.application.port.out.GetUserPort
import com.org.oneulsogae.core.user.command.application.port.out.SaveCompanyImageVerificationPort
import com.org.oneulsogae.core.user.command.domain.CompanyImageVerification
import com.org.oneulsogae.core.user.command.domain.User
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
	private val getUserDetailPort: GetUserDetailPort,
	private val fileStoragePort: FileStoragePort,
	private val saveCompanyImageVerificationPort: SaveCompanyImageVerificationPort,
) : SubmitCompanyImageVerificationUseCase {

	@Transactional
	override fun submit(userId: Long, command: SubmitCompanyImageVerificationCommand): CompanyImageVerification {
		val user: User = getUserPort.findById(userId)
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다: $userId")

		// 잘못된 입력이 S3에 올라가지 않도록 업로드 전에 파일·회사명을 모두 검증한다. (검증 실패로 롤백돼도 S3 고아 객체가 남지 않게)
		CompanyImageVerification.validateUpload(command.contentType, command.size)
		CompanyImageVerification.validateCompanyName(command.companyName)
		val contentType: String = command.contentType!!

		// 승인 시 프로필 회사명이 덮어써지므로, 제출 시점의 프로필 회사명을 이전 회사명으로 스냅샷해 심사 상세에서 보여준다.
		val previousCompanyName: String? = getUserDetailPort.findByUserId(user.id)?.companyName

		val key: String = objectKey(user.id, contentType)
		fileStoragePort.upload(key, command.content, contentType)

		return saveCompanyImageVerificationPort.save(
			CompanyImageVerification.create(
				userId = user.id,
				imageKey = key,
				companyName = command.companyName,
				previousCompanyName = previousCompanyName,
			),
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
