package com.org.meeple.common.user

enum class Role {
	USER,
	ADMIN,
	;

	/** Spring Security 권한 문자열로 변환한다. (e.g. ROLE_USER) */
	fun authority(): String = "ROLE_$name"
}
