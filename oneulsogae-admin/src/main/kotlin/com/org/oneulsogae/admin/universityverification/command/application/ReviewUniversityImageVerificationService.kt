package com.org.oneulsogae.admin.universityverification.command.application

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.universityverification.command.application.port.`in`.ReviewUniversityImageVerificationUseCase
import com.org.oneulsogae.admin.universityverification.command.application.port.out.GetUniversityImageVerificationPort
import com.org.oneulsogae.admin.universityverification.command.application.port.out.SaveUniversityImageVerificationPort
import com.org.oneulsogae.admin.universityverification.command.application.port.out.UpdateUserUniversityNamePort
import com.org.oneulsogae.admin.universityverification.command.domain.AdminUniversityImageVerification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [ReviewUniversityImageVerificationUseCase] 구현. 어드민이 학교 서류 인증을 승인/반려한다.
 * 승인: 인증 상태를 APPROVED로 바꾸고 어드민이 기입한 학교명을 유저 프로필에 확정한다.
 * 반려: 인증 상태만 REJECTED로 바꾼다.
 * (회사 인증과 달리 매칭 읽기 모델(match_user)에는 학교명이 없어 프로필만 갱신한다)
 */
@Service
@Transactional
class ReviewUniversityImageVerificationService(
	private val getUniversityImageVerificationPort: GetUniversityImageVerificationPort,
	private val saveUniversityImageVerificationPort: SaveUniversityImageVerificationPort,
	private val updateUserUniversityNamePort: UpdateUserUniversityNamePort,
) : ReviewUniversityImageVerificationUseCase {

	override fun approve(id: Long, universityName: String) {
		val verification: AdminUniversityImageVerification = getUniversityImageVerificationPort.findById(id)
			?: throw AdminException(
				AdminErrorCode.UNIVERSITY_IMAGE_VERIFICATION_NOT_FOUND,
				"학교 인증을 찾을 수 없습니다: $id",
			)
		saveUniversityImageVerificationPort.save(verification.approve())
		updateUserUniversityNamePort.updateUniversityName(verification.userId, universityName)
	}

	override fun reject(id: Long, reason: String?) {
		val verification: AdminUniversityImageVerification = getUniversityImageVerificationPort.findById(id)
			?: throw AdminException(
				AdminErrorCode.UNIVERSITY_IMAGE_VERIFICATION_NOT_FOUND,
				"학교 인증을 찾을 수 없습니다: $id",
			)
		saveUniversityImageVerificationPort.save(verification.reject(reason))
	}
}
