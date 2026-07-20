package com.org.oneulsogae.api.docs

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import io.kotest.matchers.string.shouldNotContain
import org.hamcrest.Matchers.hasKey
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.startsWith

/**
 * springdoc-openapi 문서 노출 E2E 테스트.
 * 인증 없이 OpenAPI JSON(/v3/api-docs)에 접근 가능하고(보안 permit 동작 검증), 등록된 엔드포인트가 문서에 포함되는지 검증한다.
 */
class OpenApiDocsE2ETest : AbstractIntegrationSupport({

	describe("GET /v3/api-docs") {

		context("인증 없이 OpenAPI 문서를 요청하면") {
			it("200과 OpenAPI 3 스펙을 반환하고 등록된 경로를 포함한다") {
				get("/v3/api-docs") expect {
					status(200)
					body("openapi", startsWith("3"))
					body("paths", hasKey("/users/v1/ideal-type"))
					// 미팅(2:2 팀 매칭) 엔드포인트가 활성화되어 문서에 포함된다.
					body("paths", hasKey("/teams/v1/invitation"))
					body("paths", hasKey("/team-matches/v1/meeting-tab"))
					// 모임(오프라인/gathering) 엔드포인트는 비활성화되어 문서에 포함되지 않는다.
					body("paths", not(hasKey("/offline/v1/gatherings")))
					body("paths", not(hasKey("/gatherings/v1/member-verifications")))
				}
			}
		}

		context("인증 주입 파라미터(@LoginUser AuthUser)는") {
			it("요청 파라미터·스키마로 노출되지 않는다") {
				val apiDocs: String = get("/v3/api-docs").extract().asString()

				apiDocs shouldNotContain "AuthUser"
			}
		}
	}
})
