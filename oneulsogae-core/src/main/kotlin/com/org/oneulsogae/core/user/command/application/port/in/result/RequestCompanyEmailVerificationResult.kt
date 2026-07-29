package com.org.oneulsogae.core.user.command.application.port.`in`.result

import com.org.oneulsogae.core.user.command.domain.CompanyEmailVerification
import com.org.oneulsogae.core.user.query.dto.UserCompany

/**
 * 회사 이메일 인증 요청 결과.
 * 도메인에 매핑된 회사가 하나(또는 사용자가 지정)면 인증번호를 발송하고([Sent]),
 * 같은 도메인을 쓰는 회사가 여럿인데 아직 회사를 지정하지 않았으면 발송 없이 후보 목록을 돌려준다([CompanySelectionRequired]).
 */
sealed interface RequestCompanyEmailVerificationResult {

	/** 인증번호 발송 완료. */
	data class Sent(val verification: CompanyEmailVerification) : RequestCompanyEmailVerificationResult

	/** 같은 도메인 회사가 여럿이라 사용자의 회사 선택이 필요. (메일 발송 등 부수효과 없음) */
	data class CompanySelectionRequired(val candidates: List<UserCompany>) : RequestCompanyEmailVerificationResult
}
