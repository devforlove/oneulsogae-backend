package com.org.oneulsogae.api.notification

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.notification.command.entity.QNotificationPreferenceEntity
import io.restassured.RestAssured
import io.restassured.http.ContentType

class NotificationPreferenceE2ETest : AbstractIntegrationSupport() {

	init {

		describe("GET /notification-preferences/v1") {

			context("설정한 적 없는 사용자가 조회하면") {
				it("기본값을 반환한다 (marketing만 false)") {
					val userId = 71001L

					RestAssured.given()
						.header("Authorization", "Bearer ${accessTokenFor(userId)}")
						.`when`()
						.get("/notification-preferences/v1")
						.then()
						.statusCode(200)
						.body("data.push", org.hamcrest.Matchers.equalTo(true))
						.body("data.oneToOne", org.hamcrest.Matchers.equalTo(true))
						.body("data.meeting", org.hamcrest.Matchers.equalTo(true))
						.body("data.team", org.hamcrest.Matchers.equalTo(true))
						.body("data.message", org.hamcrest.Matchers.equalTo(true))
						.body("data.marketing", org.hamcrest.Matchers.equalTo(false))
				}
			}
		}

		describe("PUT /notification-preferences/v1") {

			context("설정을 저장한 뒤 다시 조회하면") {
				it("저장한 값이 그대로 반환된다 (upsert, idempotent)") {
					val userId = 71002L
					val token = accessTokenFor(userId)
					val body = mapOf(
						"push" to true,
						"oneToOne" to false,
						"meeting" to true,
						"team" to false,
						"message" to true,
						"marketing" to true,
					)

					// 1차 저장
					RestAssured.given()
						.header("Authorization", "Bearer $token")
						.contentType(ContentType.JSON)
						.body(body)
						.`when`()
						.put("/notification-preferences/v1")
						.then()
						.statusCode(200)

					// 동일 본문 재저장(멱등)
					RestAssured.given()
						.header("Authorization", "Bearer $token")
						.contentType(ContentType.JSON)
						.body(body)
						.`when`()
						.put("/notification-preferences/v1")
						.then()
						.statusCode(200)

					// 조회로 검증
					RestAssured.given()
						.header("Authorization", "Bearer $token")
						.`when`()
						.get("/notification-preferences/v1")
						.then()
						.statusCode(200)
						.body("data.oneToOne", org.hamcrest.Matchers.equalTo(false))
						.body("data.team", org.hamcrest.Matchers.equalTo(false))
						.body("data.marketing", org.hamcrest.Matchers.equalTo(true))
				}
			}
		}

		afterTest {
			IntegrationUtil.deleteAll(QNotificationPreferenceEntity.notificationPreferenceEntity)
		}
	}
}
