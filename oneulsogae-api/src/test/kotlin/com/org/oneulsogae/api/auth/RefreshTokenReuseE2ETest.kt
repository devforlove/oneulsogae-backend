package com.org.oneulsogae.api.auth

import com.org.oneulsogae.auth.PrincipalDetails
import com.org.oneulsogae.auth.jwt.IssuedTokens
import com.org.oneulsogae.auth.jwt.RefreshTokenService
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.infra.auth.entity.QRefreshTokenEntity
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * refresh token 재사용 탐지 테스트.
 * 이미 회전(폐기)된 토큰이 다시 들어오면 탈취로 보고 그 사용자의 **모든** 토큰이 폐기되어야 한다.
 */
class RefreshTokenReuseE2ETest(
	private val refreshTokenService: RefreshTokenService,
) : AbstractIntegrationSupport({

	describe("RefreshTokenService.rotate") {
		context("이미 회전된 refresh token이 다시 들어오면") {
			it("탈취로 보고 그 사용자의 모든 토큰을 폐기한다") {
				val userId = 9201L
				val authentication: Authentication = authenticationFor(userId, "reuse@test.com")
				val first: IssuedTokens = refreshTokenService.issue(authentication)
				// 정상 회전 — first는 폐기되고 second가 활성이 된다.
				refreshTokenService.rotate(first.refreshToken)

				// 탈취된 first를 다시 사용.
				shouldThrowAny { refreshTokenService.rotate(first.refreshToken) }

				activeTokenCountOf(userId) shouldBe 0
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QRefreshTokenEntity.refreshTokenEntity)
	}
})

/** 아직 폐기되지 않은(revoked=false) 토큰 수. */
private fun activeTokenCountOf(userId: Long): Long {
	val token: QRefreshTokenEntity = QRefreshTokenEntity.refreshTokenEntity
	return IntegrationUtil.getQuery()
		.select(token.count())
		.from(token)
		.where(token.userId.eq(userId), token.revoked.isFalse)
		.fetchFirst() ?: 0L
}

private fun authenticationFor(userId: Long, email: String): Authentication {
	val authorities: List<SimpleGrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_USER"))
	val principal = PrincipalDetails(email = email, id = userId, authorities = authorities)
	return UsernamePasswordAuthenticationToken(principal, "", authorities)
}
