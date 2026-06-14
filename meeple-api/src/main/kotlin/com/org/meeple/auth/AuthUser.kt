package com.org.meeple.auth

/**
 * 컨트롤러에서 사용하는 인증 사용자 정보.
 * accessToken에서 복원한 식별 정보만 담는다. (영속 엔티티가 아님)
 */
data class AuthUser(
	val id: Long,
	val email: String,
) {
	companion object {
		fun from(principal: PrincipalDetails): AuthUser =
			AuthUser(id = principal.id, email = principal.email)
	}
}
