package com.org.oneulsogae.auth

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User

/**
 * 인증 주체. OAuth2 로그인 결과와 JWT 인가 모두에서 동일한 principal로 사용한다.
 */
class PrincipalDetails(
	val email: String,
	val id: Long,
	private val authorities: Collection<GrantedAuthority>,
	private val attributes: Map<String, Any> = emptyMap(),
) : OAuth2User {

	val username: String get() = email

	override fun getName(): String = id.toString()

	override fun getAttributes(): Map<String, Any> = attributes

	override fun getAuthorities(): Collection<GrantedAuthority> = authorities
}
