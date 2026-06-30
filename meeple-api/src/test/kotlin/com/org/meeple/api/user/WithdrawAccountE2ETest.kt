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
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

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

				// 소프트삭제 직접 검증: 행이 존재하고 deleted_at이 set, status는 원본(ACTIVE) 유지
				val row: Array<Any?> = IntegrationUtil.nativeQuerySingleOrNull(
					"select status, deleted_at from users where id = $userId"
				)!!
				row[0] shouldBe "ACTIVE"     // status 원본 보존
				row[1] shouldNotBe null      // deleted_at 설정됨
			}
		}

		context("이미 탈퇴한 사용자가 다시 탈퇴를 요청하면") {
			it("소프트삭제로 findById null → 404 (USER-001)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
				).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId))

				val token: String = accessTokenFor(userId)

				// 첫 번째 탈퇴
				delete("/users/v1/account") {
					bearer(token)
				} expect { status(200) }

				// 두 번째 탈퇴 — 소프트삭제로 findById가 null → USER_NOT_FOUND
				delete("/users/v1/account") {
					bearer(token)
				} expect {
					status(404)
					body("error.code", "USER-001")
				}
			}
		}

		context("온보딩 단계 사용자(user_details 없음)가 탈퇴를 요청하면") {
			it("200으로 소프트삭제된다") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ONBOARDING),
				).id!!
				// user_details 미저장(온보딩 미완료)

				delete("/users/v1/account") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
				}

				activeUserCountOf(userId) shouldBe 0

				// 소프트삭제 직접 검증: status 원본(ONBOARDING) 보존
				val row: Array<Any?> = IntegrationUtil.nativeQuerySingleOrNull(
					"select status, deleted_at from users where id = $userId"
				)!!
				row[0] shouldBe "ONBOARDING"
				row[1] shouldNotBe null
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QRefreshTokenEntity.refreshTokenEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
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
