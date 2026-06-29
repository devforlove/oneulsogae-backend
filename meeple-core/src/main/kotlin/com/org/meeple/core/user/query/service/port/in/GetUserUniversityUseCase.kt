package com.org.meeple.core.user.query.service.port.`in`

/**
 * 학교 매핑 조회 인포트(유스케이스).
 * 학교 이메일의 도메인으로 매핑된 학교명을 조회한다. 다른 도메인은 이 인포트를 통해 학교 매핑을 참조한다.
 */
interface GetUserUniversityUseCase {

	/** 학교 이메일의 도메인으로 매핑된 학교명을 조회한다. 매핑이 없으면 null. */
	fun findUniversityNameByEmail(universityEmail: String): String?
}
