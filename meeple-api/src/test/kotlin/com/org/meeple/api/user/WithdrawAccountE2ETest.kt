package com.org.meeple.api.user

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.delete
import com.org.meeple.common.integration.expect
import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.auth.entity.QRefreshTokenEntity
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.RefreshTokenEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.matchuser.command.entity.QMatchUserEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import io.kotest.matchers.shouldBe

/**
 * `DELETE /users/v1/account` E2E.
 * 탈퇴 시 계정이 소프트삭제(조회 불가)되고, refresh token이 폐기되며, 매칭 읽기모델에서 제거되는지 검증한다.
 */
class WithdrawAccountE2ETest : AbstractIntegrationSupport({

	describe("DELETE /users/v1/account") {

		context("로그인 사용자가 탈퇴를 요청하면") {
			it("계정이 소프트삭제되고 토큰 폐기·매칭 제거된다 (200)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
				).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId))
				IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId))
				IntegrationUtil.persist(RefreshTokenEntityFixture.create(userId = userId))

				delete("/users/v1/account") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
				}

				// 소프트삭제 → 일반 조회(@SQLRestriction)에서 제외
				activeUserCountOf(userId) shouldBe 0
				// 매칭 읽기모델 제거(하드삭제)
				matchUserCountOf(userId) shouldBe 0
				// refresh token 폐기
				validRefreshTokenCountOf(userId) shouldBe 0
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QRefreshTokenEntity.refreshTokenEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})

private fun activeUserCountOf(userId: Long): Int =
	IntegrationUtil.getQuery()
		.selectFrom(QUserEntity.userEntity)
		.where(QUserEntity.userEntity.id.eq(userId))
		.fetch().size

private fun matchUserCountOf(userId: Long): Int =
	IntegrationUtil.getQuery()
		.selectFrom(QMatchUserEntity.matchUserEntity)
		.where(QMatchUserEntity.matchUserEntity.userId.eq(userId))
		.fetch().size

private fun validRefreshTokenCountOf(userId: Long): Int =
	IntegrationUtil.getQuery()
		.selectFrom(QRefreshTokenEntity.refreshTokenEntity)
		.where(
			QRefreshTokenEntity.refreshTokenEntity.userId.eq(userId),
			QRefreshTokenEntity.refreshTokenEntity.revoked.isFalse,
		)
		.fetch().size
