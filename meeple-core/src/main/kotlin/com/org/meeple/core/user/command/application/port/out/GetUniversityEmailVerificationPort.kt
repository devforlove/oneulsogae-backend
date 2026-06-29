package com.org.meeple.core.user.command.application.port.out

import com.org.meeple.core.user.command.domain.UniversityEmailVerification

/**
 * 학교 이메일 인증 조회 아웃포트.
 * 도메인 모델([UniversityEmailVerification])만을 반환하며, 실제 구현은 infra 레이어의 어댑터가 담당한다.
 */
interface GetUniversityEmailVerificationPort {

	/**
	 * 해당 사용자의 가장 최근 인증 요청 1건을 조회한다. 없으면 null.
	 * 만료 여부는 호출하는 서비스가 판단한다.
	 */
	fun findLatestByUserId(userId: Long): UniversityEmailVerification?
}
