package com.org.meeple.admin.memberverification.command.application

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.memberverification.command.application.port.`in`.ReviewMemberVerificationUseCase
import com.org.meeple.admin.memberverification.command.application.port.out.GetMemberVerificationPort
import com.org.meeple.admin.memberverification.command.application.port.out.SaveMemberVerificationPort
import com.org.meeple.admin.memberverification.command.application.port.out.UpdateMatchUserCompanyNamePort
import com.org.meeple.admin.memberverification.command.application.port.out.UpdateUserVerifiedProfilePort
import com.org.meeple.admin.memberverification.command.domain.AdminMemberVerification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [ReviewMemberVerificationUseCase] 구현. 어드민이 멤버 인증을 승인/반려한다.
 * 승인: 상태를 APPROVED로 바꾸고 어드민이 확정한 회사명·직종·직장 상세를 유저 프로필(user_details)에 반영한다.
 * 반려: 상태를 REJECTED로 바꾸고 사유를 남긴다.
 * (가입 상태 전환·코인 등 부가 효과는 범위 밖)
 *
 * 승인 시 유저 프로필과 매칭 읽기 모델(match_user)의 회사명을 함께 갱신해 같은-회사 소개 차단이 스테일해지지 않게 한다.
 * (match_user 행이 없는(온보딩) 유저는 회사명 동기화가 no-op)
 */
@Service
@Transactional
class ReviewMemberVerificationService(
	private val getMemberVerificationPort: GetMemberVerificationPort,
	private val saveMemberVerificationPort: SaveMemberVerificationPort,
	private val updateUserVerifiedProfilePort: UpdateUserVerifiedProfilePort,
	private val updateMatchUserCompanyNamePort: UpdateMatchUserCompanyNamePort,
) : ReviewMemberVerificationUseCase {

	override fun approve(id: Long, companyName: String, jobCategory: String, jobDetail: String) {
		val verification: AdminMemberVerification = load(id)
		saveMemberVerificationPort.save(verification.approve())
		updateUserVerifiedProfilePort.updateVerifiedProfile(verification.userId, companyName, jobCategory, jobDetail)
		updateMatchUserCompanyNamePort.updateCompanyName(verification.userId, companyName)
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
