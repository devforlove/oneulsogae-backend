package com.org.meeple.admin.companyverification.command.application

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.companyverification.command.application.port.`in`.ReviewCompanyImageVerificationUseCase
import com.org.meeple.admin.companyverification.command.application.port.out.GetCompanyImageVerificationPort
import com.org.meeple.admin.companyverification.command.application.port.out.SaveCompanyImageVerificationPort
import com.org.meeple.admin.companyverification.command.application.port.out.UpdateUserCompanyNamePort
import com.org.meeple.admin.companyverification.command.domain.AdminCompanyImageVerification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [ReviewCompanyImageVerificationUseCase] 구현. 어드민이 직장 서류 인증을 승인/반려한다.
 * 승인: 인증 상태를 APPROVED로 바꾸고 어드민이 기입한 회사명을 유저 프로필에 확정한다.
 * 반려: 인증 상태만 REJECTED로 바꾼다.
 * (가입 상태 전환·코인·추천 등 부가 효과는 범위 밖)
 */
@Service
@Transactional
class ReviewCompanyImageVerificationService(
	private val getCompanyImageVerificationPort: GetCompanyImageVerificationPort,
	private val saveCompanyImageVerificationPort: SaveCompanyImageVerificationPort,
	private val updateUserCompanyNamePort: UpdateUserCompanyNamePort,
) : ReviewCompanyImageVerificationUseCase {

	override fun approve(id: Long, companyName: String) {
		val verification: AdminCompanyImageVerification = getCompanyImageVerificationPort.findById(id)
			?: throw AdminException(
				AdminErrorCode.COMPANY_IMAGE_VERIFICATION_NOT_FOUND,
				"직장 인증을 찾을 수 없습니다: $id",
			)
		saveCompanyImageVerificationPort.save(verification.approve())
		updateUserCompanyNamePort.updateCompanyName(verification.userId, companyName)
	}

	override fun reject(id: Long) {
		val verification: AdminCompanyImageVerification = getCompanyImageVerificationPort.findById(id)
			?: throw AdminException(
				AdminErrorCode.COMPANY_IMAGE_VERIFICATION_NOT_FOUND,
				"직장 인증을 찾을 수 없습니다: $id",
			)
		saveCompanyImageVerificationPort.save(verification.reject())
	}
}
