package com.org.oneulsogae.core.gathering.command.application

import com.org.oneulsogae.core.gathering.command.application.port.`in`.SubmitMemberVerificationUseCase
import com.org.oneulsogae.core.gathering.command.application.port.`in`.command.SubmitMemberVerificationCommand
import com.org.oneulsogae.core.gathering.command.application.port.out.FileStoragePort
import com.org.oneulsogae.core.gathering.command.application.port.out.SaveMemberVerificationPort
import com.org.oneulsogae.core.gathering.command.domain.MemberVerification
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserByIdUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * [SubmitMemberVerificationUseCase] 구현.
 * 업로드한 사진 3종(얼굴·신분증·서류)과 직업 정보를 검증한 뒤 파일을 S3에 비공개로 올리고([FileStoragePort]),
 * 오브젝트 키 3개와 심사 상태(PENDING)를 member_verifications에 저장한다.
 * 자동 검증이 불가능하므로 이 시점에 가입 상태는 바꾸지 않는다. (승인/반려는 어드민 심사 — 이번 범위 밖)
 * 유저 존재 확인은 user 도메인 in-port([GetUserByIdUseCase])로 위임한다(없으면 그쪽이 USER_NOT_FOUND).
 */
@Service
class SubmitMemberVerificationService(
	private val getUserByIdUseCase: GetUserByIdUseCase,
	private val fileStoragePort: FileStoragePort,
	private val saveMemberVerificationPort: SaveMemberVerificationPort,
) : SubmitMemberVerificationUseCase {

	@Transactional
	override fun submit(userId: Long, command: SubmitMemberVerificationCommand): MemberVerification {
		getUserByIdUseCase.getById(userId) // 존재 검증 (없으면 USER_NOT_FOUND)

		// 잘못된 입력이 S3에 올라가지 않도록 업로드 전에 파일 3개·직업 정보를 모두 검증한다. (검증 실패로 롤백돼도 S3 고아 객체가 남지 않게)
		MemberVerification.validatePhoto(command.face.contentType, command.face.size)
		MemberVerification.validatePhoto(command.idCard.contentType, command.idCard.size)
		MemberVerification.validateDocument(command.document.contentType, command.document.size)
		MemberVerification.validateJobInfo(command.jobCategory, command.jobDetail)

		val faceImageKey: String = uploadFile(userId, command.face)
		val idCardImageKey: String = uploadFile(userId, command.idCard)
		val documentImageKey: String = uploadFile(userId, command.document)

		return saveMemberVerificationPort.save(
			MemberVerification.create(
				userId = userId,
				jobCategory = command.jobCategory,
				jobDetail = command.jobDetail,
				faceImageKey = faceImageKey,
				idCardImageKey = idCardImageKey,
				documentImageKey = documentImageKey,
			),
		)
	}

	/** 파일 한 개를 사용자별 폴더 아래 충돌 없는 키로 업로드하고 그 키를 반환한다. (예: member-verifications/42/{uuid}.jpg) */
	private fun uploadFile(userId: Long, file: SubmitMemberVerificationCommand.FilePart): String {
		// 검증을 통과한 파일이므로 contentType은 null이 아니다.
		val contentType: String = file.contentType!!
		val extension: String = MemberVerification.extensionOf(contentType)
		val key: String = "$KEY_PREFIX/$userId/${UUID.randomUUID()}.$extension"
		return fileStoragePort.upload(key, file.content, contentType)
	}

	companion object {
		private const val KEY_PREFIX: String = "member-verifications"
	}
}
