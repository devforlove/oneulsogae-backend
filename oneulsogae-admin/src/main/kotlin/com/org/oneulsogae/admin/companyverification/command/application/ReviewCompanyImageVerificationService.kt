package com.org.oneulsogae.admin.companyverification.command.application

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.companyverification.command.application.port.`in`.ReviewCompanyImageVerificationUseCase
import com.org.oneulsogae.admin.companyverification.command.application.port.out.GetCompanyImageVerificationPort
import com.org.oneulsogae.admin.companyverification.command.application.port.out.SaveCompanyImageVerificationPort
import com.org.oneulsogae.admin.companyverification.command.application.port.out.UpdateMatchUserCompanyNamePort
import com.org.oneulsogae.admin.companyverification.command.application.port.out.UpdateUserCompanyNamePort
import com.org.oneulsogae.admin.companyverification.command.domain.AdminCompanyImageVerification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [ReviewCompanyImageVerificationUseCase] 구현. 어드민이 직장 서류 인증을 승인/반려한다.
 * 승인: 인증 상태를 APPROVED로 바꾸고 어드민이 기입한 회사명을 유저 프로필에 확정한다.
 * 반려: 인증 상태만 REJECTED로 바꾼다.
 * (가입 상태 전환·코인·추천 등 부가 효과는 범위 밖)
 *
 * 승인 시 유저 프로필(user_details)과 매칭 읽기 모델(match_user)의 회사명을 함께 갱신해 같은-회사 소개 차단이 스테일해지지 않게 한다.
 * (match_user 행이 없는(온보딩) 유저는 no-op)
 */
@Service
@Transactional
class ReviewCompanyImageVerificationService(
	private val getCompanyImageVerificationPort: GetCompanyImageVerificationPort,
	private val saveCompanyImageVerificationPort: SaveCompanyImageVerificationPort,
	private val updateUserCompanyNamePort: UpdateUserCompanyNamePort,
	private val updateMatchUserCompanyNamePort: UpdateMatchUserCompanyNamePort,
) : ReviewCompanyImageVerificationUseCase {

	override fun approve(id: Long, companyName: String) {
		val verification: AdminCompanyImageVerification = getCompanyImageVerificationPort.findById(id)
			?: throw AdminException(
				AdminErrorCode.COMPANY_IMAGE_VERIFICATION_NOT_FOUND,
				"직장 인증을 찾을 수 없습니다: $id",
			)
		saveCompanyImageVerificationPort.save(verification.approve())
		updateUserCompanyNamePort.updateCompanyName(verification.userId, companyName)
		updateMatchUserCompanyNamePort.updateCompanyName(verification.userId, companyName)
	}

	override fun reject(id: Long, reason: String?) {
		val verification: AdminCompanyImageVerification = getCompanyImageVerificationPort.findById(id)
			?: throw AdminException(
				AdminErrorCode.COMPANY_IMAGE_VERIFICATION_NOT_FOUND,
				"직장 인증을 찾을 수 없습니다: $id",
			)
		saveCompanyImageVerificationPort.save(verification.reject(reason))
	}
}
