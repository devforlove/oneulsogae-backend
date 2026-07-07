package com.org.meeple.api.admin

import com.org.meeple.common.integration.AbstractIntegrationSupport
import io.restassured.RestAssured
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.matchesPattern

/**
 * 어드민 소개 이미지 업로드 API E2E 테스트.
 * (실제 S3 업로드는 [com.org.meeple.common.config.TestFileStorageConfig]의 페이크로 대체 — 넘긴 key를 그대로 저장)
 */
class AdminImageUploadE2ETest : AbstractIntegrationSupport({

	describe("POST /admin/v1/images") {

		context("어드민이 PNG를 올리면") {
			it("gathering-descriptions/ 프리픽스의 key를 반환한다 (200)") {
				RestAssured.given()
					.header("Authorization", "Bearer ${adminAccessTokenFor(9901L)}")
					.multiPart("image", "a.png", "fake-png-bytes".toByteArray(), "image/png")
					.post("/admin/v1/images")
					.then()
					.statusCode(200)
					.body("success", equalTo(true))
					.body("data.key", matchesPattern("gathering-descriptions/.*\\.png"))
			}
		}

		context("허용하지 않는 형식(text/plain)을 올리면") {
			it("400(GATHER-009)을 반환한다") {
				RestAssured.given()
					.header("Authorization", "Bearer ${adminAccessTokenFor(9901L)}")
					.multiPart("image", "a.txt", "not-an-image".toByteArray(), "text/plain")
					.post("/admin/v1/images")
					.then()
					.statusCode(400)
					.body("success", equalTo(false))
					.body("error.code", equalTo("GATHER-009"))
			}
		}

		context("5MB를 초과하는 이미지를 올리면") {
			it("400(GATHER-010)을 반환한다") {
				val tooLarge = ByteArray(6 * 1024 * 1024)
				RestAssured.given()
					.header("Authorization", "Bearer ${adminAccessTokenFor(9901L)}")
					.multiPart("image", "big.png", tooLarge, "image/png")
					.post("/admin/v1/images")
					.then()
					.statusCode(400)
					.body("success", equalTo(false))
					.body("error.code", equalTo("GATHER-010"))
			}
		}

		context("ADMIN 권한이 없으면") {
			it("일반 사용자 토큰은 403을 반환한다") {
				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(3001L)}")
					.multiPart("image", "a.png", "fake-png-bytes".toByteArray(), "image/png")
					.post("/admin/v1/images")
					.then()
					.statusCode(403)
			}
		}

		context("토큰이 없으면") {
			it("401을 반환한다") {
				RestAssured.given()
					.multiPart("image", "a.png", "fake-png-bytes".toByteArray(), "image/png")
					.post("/admin/v1/images")
					.then()
					.statusCode(401)
			}
		}
	}
})
