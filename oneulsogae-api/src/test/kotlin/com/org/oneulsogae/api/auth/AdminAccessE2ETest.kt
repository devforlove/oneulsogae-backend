package com.org.oneulsogae.api.auth

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get

/**
 * `/admin` 하위 경로 인가 규칙 E2E 테스트.
 * 어드민도 같은 OAuth2+JWT 체계를 쓰므로, 토큰의 ROLE_ADMIN 권한만으로 접근이 갈리는지 검증한다.
 * (아직 어드민 컨트롤러가 없어 인가 통과 시 404, 차단 시 401/403이 내려온다)
 */
class AdminAccessE2ETest : AbstractIntegrationSupport({

	describe("GET /admin/**") {

		context("토큰 없이 요청하면") {
			it("401을 반환한다") {
				get("/admin/anything") expect {
					status(401)
				}
			}
		}

		context("ROLE_USER 토큰으로 요청하면") {
			it("403을 반환한다") {
				get("/admin/anything") {
					bearer(accessTokenFor(6001L))
				} expect {
					status(403)
				}
			}
		}

		context("ROLE_ADMIN 토큰으로 요청하면") {
			it("인가를 통과한다 (매핑된 핸들러가 없어 404)") {
				get("/admin/anything") {
					bearer(adminAccessTokenFor(6002L))
				} expect {
					status(404)
				}
			}
		}
	}
})
