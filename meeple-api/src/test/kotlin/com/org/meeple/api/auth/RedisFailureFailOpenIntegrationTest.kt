package com.org.meeple.api.auth

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.infra.user.command.entity.UserEntity
import com.redis.testcontainers.RedisContainer
import org.springframework.beans.factory.annotation.Autowired
import org.testcontainers.DockerClientFactory

/**
 * 단일 활성 세션의 Redis 장애 정책(fail-open) E2E.
 *
 * 단일 활성 세션은 stateless JWT(서명·만료 검증) 위에 얹은 정책이므로, Redis 장애가 인증 전체를 막는
 * 단일 장애점이 되어선 안 된다. Redis 컨테이너를 pause해 "응답 불능"을 만들고, 인증 요청이 짧은 명령
 * 타임아웃([spring.data.redis.timeout]) 뒤 fail-open으로 통과(200)하는지, 복구 후 정상 동작하는지 검증한다.
 *
 * (포트가 바뀌는 stop/start 대신 pause/unpause를 써서 같은 접속 정보를 유지하고, 공유 컨텍스트의 다른
 * 테스트에 영향이 가지 않도록 finally에서 반드시 unpause 한다)
 */
class RedisFailureFailOpenIntegrationTest : AbstractIntegrationSupport() {

	@Autowired
	private lateinit var redisContainer: RedisContainer

	init {
		describe("Redis 장애 시 단일 활성 세션 fail-open") {

			it("Redis가 응답 불능이어도 인증 요청은 fail-open으로 통과하고, 복구 후 정상 동작한다") {
				val user: UserEntity = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "redis-failopen", email = "failopen@test.com", status = UserStatus.ACTIVE),
				)
				// healthy Redis 상태에서 활성 세션 마커가 등록된 정상 토큰.
				val token: String = accessTokenFor(user.id!!)

				// 1) Redis 정상 → 세션 검사 통과.
				get("/auth/v1/me") { bearer(token) } expect { status(200) }

				// 2) Redis 응답 불능(pause) → 세션 검사는 타임아웃→fail-open(허용)으로 떨어져 요청은 그대로 통과.
				pauseRedis()
				try {
					get("/auth/v1/me") { bearer(token) } expect { status(200) }
				} finally {
					unpauseRedis()
				}

				// 3) Redis 복구 → 다시 정상 동작.
				get("/auth/v1/me") { bearer(token) } expect { status(200) }
			}
		}

		afterTest {
			IntegrationUtil.deleteAll(QUserEntity.userEntity)
		}
	}

	private fun pauseRedis() {
		DockerClientFactory.instance().client()
			.pauseContainerCmd(redisContainer.containerId)
			.exec()
	}

	private fun unpauseRedis() {
		DockerClientFactory.instance().client()
			.unpauseContainerCmd(redisContainer.containerId)
			.exec()
	}
}
