package com.org.oneulsogae.admin.memberverification.command.application

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.memberverification.command.application.port.`in`.ReviewMemberVerificationUseCase
import com.org.oneulsogae.admin.memberverification.command.application.port.out.GetMemberVerificationPort
import com.org.oneulsogae.admin.memberverification.command.application.port.out.GetVerifiedUserProfilePort
import com.org.oneulsogae.admin.memberverification.command.application.port.out.SaveGatheringProfilePort
import com.org.oneulsogae.admin.memberverification.command.application.port.out.SaveMemberVerificationPort
import com.org.oneulsogae.admin.memberverification.command.application.port.out.UpdateMatchUserCompanyNamePort
import com.org.oneulsogae.admin.memberverification.command.application.port.out.UpdateUserCompanyNamePort
import com.org.oneulsogae.admin.memberverification.command.application.port.out.VerifiedUserProfile
import com.org.oneulsogae.admin.memberverification.command.domain.AdminMemberVerification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [ReviewMemberVerificationUseCase] 구현. 어드민이 멤버 인증을 승인/반려한다.
 * 승인:
 *  - 상태를 APPROVED로.
 *  - 회사명([companyName])을 유저 프로필(user_details)과 매칭 읽기 모델(match_user)에 확정한다.
 *    (같은-회사 소개 차단이 스테일해지지 않게 함. match_user 행이 없으면 no-op)
 *  - 직종·직장 상세와, user_details에서 가져온 생일·키를 gathering_profile에 저장한다. (나이는 조회 시 생일로부터 계산)
 * 반려: 상태를 REJECTED로 바꾸고 사유를 남긴다.
 */
@Service
@Transactional
class ReviewMemberVerificationService(
	private val getMemberVerificationPort: GetMemberVerificationPort,
	private val saveMemberVerificationPort: SaveMemberVerificationPort,
	private val updateUserCompanyNamePort: UpdateUserCompanyNamePort,
	private val updateMatchUserCompanyNamePort: UpdateMatchUserCompanyNamePort,
	private val getVerifiedUserProfilePort: GetVerifiedUserProfilePort,
	private val saveGatheringProfilePort: SaveGatheringProfilePort,
) : ReviewMemberVerificationUseCase {

	override fun approve(id: Long, companyName: String, jobCategory: String, jobDetail: String) {
		val verification: AdminMemberVerification = load(id)
		saveMemberVerificationPort.save(verification.approve())

		updateUserCompanyNamePort.updateCompanyName(verification.userId, companyName)
		updateMatchUserCompanyNamePort.updateCompanyName(verification.userId, companyName)

		val profile: VerifiedUserProfile? = getVerifiedUserProfilePort.findProfileSource(verification.userId)
		saveGatheringProfilePort.save(
			verification.userId,
			jobCategory,
			jobDetail,
			profile?.birthday,
			profile?.height,
			profile?.profileImageCode,
		)
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
