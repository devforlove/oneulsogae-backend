package com.org.oneulsogae.auth.jwt

import com.org.oneulsogae.auth.PrincipalDetails
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

@Component
class TokenProvider(
	@Value("\${jwt.secret}")
	private val key: String,
	@Value("\${jwt.expiration_time}")
	private val expireTime: String,
	@Value("\${jwt.refresh_expiration_time}")
	private val refreshExpireTime: String,
) {

	private val secretKey = Keys.hmacShaKeyFor(key.toByteArray())

	/** access token을 발급한다. */
	fun generateAccessToken(authentication: Authentication): String =
		generateToken(authentication, expireTime.toLong())

	/**
	 * refresh token을 발급한다. 회전/폐기 추적을 위해 [tokenId]를 jti 클레임으로 심는다.
	 * 발급된 jti는 호출 측에서 저장소에 기록해 두어야 검증·회전이 가능하다.
	 */
	fun generateRefreshToken(authentication: Authentication, tokenId: String): String =
		generateToken(authentication, refreshExpireTime.toLong(), tokenId)

	/** refresh token의 jti(tokenId)를 추출한다. */
	fun getTokenId(token: String): String? = parseClaims(token).id

	/** access token 유효기간(초). 클라이언트가 만료 전 선제 갱신 타이머를 거는 데 사용한다. */
	fun accessTokenExpiresInSeconds(): Long = expireTime.toLong() / 1000

	/** 토큰 만료 시각을 반환한다. (저장소의 expiresAt 기록용) */
	fun getExpiration(token: String): LocalDateTime =
		parseClaims(token).expiration.toInstant()
			.atZone(ZoneId.systemDefault())
			.toLocalDateTime()

	fun getAuthentication(token: String): Authentication {
		val claims: Claims = parseClaims(token)

		val authorities: List<GrantedAuthority> = getAuthorities(claims)
		val userEmail: String = claims[USER_EMAIL] as? String ?: ""
		val userId: Long = (claims[USER_ID] as Number).toLong()

		val principal: PrincipalDetails = PrincipalDetails(userEmail, userId, authorities)
		return UsernamePasswordAuthenticationToken(principal, token, authorities)
	}

	fun validateToken(token: String?): Boolean {
		if (!StringUtils.hasText(token)) {
			return false
		}

		return try {
			val claims: Claims = parseClaims(token!!)
			claims.expiration.after(Date())
		} catch (e: JwtException) {
			false
		} catch (e: IllegalArgumentException) {
			false
		}
	}

	private fun getAuthorities(claims: Claims): List<GrantedAuthority> =
		(claims[KEY_ROLE] as List<*>)
			.map { it: Any? -> SimpleGrantedAuthority(it as String) }

	private fun generateToken(
		authentication: Authentication,
		expirationMillis: Long,
		tokenId: String? = null,
	): String {
		val now: Date = Date()
		val expiry: Date = Date(now.time + expirationMillis)

		val principalDetails: PrincipalDetails = authentication.principal as PrincipalDetails
		val authorities: List<String?> = authentication.authorities.map { it.authority }

		return Jwts.builder()
			.subject(authentication.name)
			.apply { tokenId?.let { id(it) } }
			.claim(KEY_ROLE, authorities)
			.claim(USER_ID, principalDetails.id)
			.claim(USER_EMAIL, principalDetails.username)
			.issuedAt(now)
			.expiration(expiry)
			.signWith(secretKey, Jwts.SIG.HS512)
			.compact()
	}

	private fun parseClaims(token: String): Claims =
		try {
			Jwts.parser()
				.verifyWith(secretKey)
				.build()
				.parseSignedClaims(token)
				.payload
		} catch (e: ExpiredJwtException) {
			e.claims
		}

	companion object {
		private const val KEY_ROLE = "role"
		private const val USER_ID = "user_id"
		private const val USER_EMAIL = "user_email"
	}
}
