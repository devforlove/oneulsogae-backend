package com.org.meeple.admin.memberverification.command.application

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.memberverification.command.application.port.`in`.ApproveMemberVerificationUseCase
import com.org.meeple.admin.memberverification.command.application.port.out.GetMemberVerificationPort
import com.org.meeple.admin.memberverification.command.application.port.out.SaveMemberVerificationPort
import com.org.meeple.admin.memberverification.command.domain.AdminMemberVerification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [ApproveMemberVerificationUseCase] 구현. 어드민이 멤버 인증을 승인한다.
 * 인증 상태를 APPROVED로 바꾼다. (가입 상태 전환·프로필 반영 등 부가 효과는 범위 밖 — 최신 제출 상태가 곧 인증 여부다)
 */
@Service
@Transactional
class ApproveMemberVerificationService(
	private val getMemberVerificationPort: GetMemberVerificationPort,
	private val saveMemberVerificationPort: SaveMemberVerificationPort,
) : ApproveMemberVerificationUseCase {

	override fun approve(id: Long) {
		val verification: AdminMemberVerification = getMemberVerificationPort.findById(id)
			?: throw AdminException(
				AdminErrorCode.MEMBER_VERIFICATION_NOT_FOUND,
				"멤버 인증을 찾을 수 없습니다: $id",
			)
		saveMemberVerificationPort.save(verification.approve())
	}
}
