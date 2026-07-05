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
 *
 * [알려진 제약 — 보류] 회사명 확정 시 match_user 읽기 모델을 동기화하지 않는다.
 * 이메일 인증(VerifyCompanyEmailService)·회사명 직접입력(ResolveCompanyNameService)은 회사명 변경 후 match_user를 동기화하지만,
 * admin은 core 비의존이라 이 경로엔 동기화가 없다. 이미 ACTIVE(match_user 행 보유)인 유저의 회사명을 어드민이 바꾸면
 * 같은-회사 소개 차단(SameCompanyIntroPredicates가 match_user.company_name 사용)이 스테일해질 수 있다.
 * 이미지 인증은 대개 온보딩 폴백(아직 ACTIVE 아님 → match_user 없음)이라 실제 영향은 좁다. 재점검 시 admin 동기화 out-port 추가를 검토한다.
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

	override fun reject(id: Long, reason: String?) {
		val verification: AdminCompanyImageVerification = getCompanyImageVerificationPort.findById(id)
			?: throw AdminException(
				AdminErrorCode.COMPANY_IMAGE_VERIFICATION_NOT_FOUND,
				"직장 인증을 찾을 수 없습니다: $id",
			)
		saveCompanyImageVerificationPort.save(verification.reject(reason))
	}
}
