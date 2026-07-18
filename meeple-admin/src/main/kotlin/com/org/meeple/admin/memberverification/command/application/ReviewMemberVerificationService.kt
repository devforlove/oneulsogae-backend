package com.org.meeple.admin.memberverification.command.application

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.memberverification.command.application.port.`in`.ReviewMemberVerificationUseCase
import com.org.meeple.admin.memberverification.command.application.port.out.GetMemberVerificationPort
import com.org.meeple.admin.memberverification.command.application.port.out.SaveMemberVerificationPort
import com.org.meeple.admin.memberverification.command.domain.AdminMemberVerification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [ReviewMemberVerificationUseCase] 구현. 어드민이 멤버 인증을 승인/반려한다.
 * 승인: 상태를 APPROVED로. 반려: 상태를 REJECTED로 바꾸고 사유를 남긴다.
 * (가입 상태 전환·프로필 반영 등 부가 효과는 범위 밖 — 최신 제출 상태가 곧 인증 여부다)
 */
@Service
@Transactional
class ReviewMemberVerificationService(
	private val getMemberVerificationPort: GetMemberVerificationPort,
	private val saveMemberVerificationPort: SaveMemberVerificationPort,
) : ReviewMemberVerificationUseCase {

	override fun approve(id: Long) {
		saveMemberVerificationPort.save(load(id).approve())
	}

	override fun reject(id: Long, reason: String?) {
		saveMemberVerificationPort.save(load(id).reject(reason))
	}

	private fun load(id: Long): AdminMemberVerification =
		getMemberVerificationPort.findById(id)
			?: throw AdminException(
				AdminErrorCode.MEMBER_VERIFICATION_NOT_FOUND,
				"멤버 인증을 찾을 수 없습니다: $id",
			)
}
