package com.org.meeple.api.admin

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.match.command.entity.QMatchEntity
import com.org.meeple.infra.match.command.entity.QMatchMemberEntity
import org.hamcrest.Matchers.greaterThanOrEqualTo

/**
 * `POST /admin/v1/matches/batch` E2E 테스트. (관리자 전용 일일 매칭 배치 수동 실행)
 *
 * 크론과 동일한 진입점을 호출해 배치를 즉시(동기) 실행하고 결과를 반환한다. ROLE_ADMIN만 접근 가능하다.
 * 실제 서버(RANDOM_PORT) + Testcontainers(MySQL/Redis)를 기동하고 HTTP를 호출한다.
 */
class AdminMatchBatchE2ETest : AbstractIntegrationSupport({

	describe("POST /admin/v1/matches/batch") {

		context("관리자가 호출하면") {
			it("배치를 실행하고 결과를 반환한다 (200)") {
				post("/admin/v1/matches/batch") {
					bearer(adminAccessTokenFor(9001L))
				} expect {
					status(200)
					body("success", true)
					// 매칭 대상 활성 유저가 없으면 소개 0건. (결과 구조가 채워져 내려온다)
					body("data.targets", greaterThanOrEqualTo(0))
					body("data.recommended", greaterThanOrEqualTo(0))
				}
			}
		}

		context("일반 사용자(ROLE_USER)가 호출하면") {
			it("403을 반환한다") {
				post("/admin/v1/matches/batch") {
					bearer(accessTokenFor(9002L))
				} expect {
					status(403)
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				post("/admin/v1/matches/batch") {} expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QMatchMemberEntity.matchMemberEntity)
		IntegrationUtil.deleteAll(QMatchEntity.matchEntity)
	}
})
