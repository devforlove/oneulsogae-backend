package com.org.meeple.core.user.command.service.port.`in`

/**
 * 회사명 직접 입력 인포트(유스케이스).
 * 도메인 매핑으로 회사명을 찾지 못한 사용자가 회사명을 직접 입력하면, 프로필에 반영하고 정식 가입(ACTIVE) 처리한다.
 */
interface ResolveCompanyNameUseCase {

	fun resolve(userId: Long, companyName: String)
}
