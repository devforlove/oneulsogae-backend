package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MatchUserEntityFixture
import com.org.oneulsogae.infra.user.command.entity.UserDetailEntity
import org.hamcrest.Matchers.contains

/**
 * `GET /users/v1/profile` E2E 테스트.
 *
 * 프로필 조회가 QueryDSL 투영으로 [com.org.oneulsogae.core.user.query.dto.UserDetailView]를 바로 만들 때,
 * `@Convert`(JSON) 컬럼인 traits/interests가 컨버터를 거쳐 `List<String>`으로 올바로 복원되는지 검증한다.
 */
class GetMyProfileE2ETest : AbstractIntegrationSupport({

	describe("GET /users/v1/profile") {

		context("traits/interests가 채워진 사용자가 조회하면") {
			it("JSON 컨버터 컬럼이 리스트로 복원돼 응답에 그대로 내려온다 (200)") {
				val userId: Long = 5001L
				IntegrationUtil.persist(
					UserDetailEntity(
						userId = userId,
						nickname = "투영유저",
						traits = listOf("운동", "독서"),
						interests = listOf("영화", "여행", "코딩"),
					),
				)

				get("/users/v1/profile") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data.nickname", "투영유저")
					body("data.traits", contains("운동", "독서"))
					body("data.interests", contains("영화", "여행", "코딩"))
				}
			}
		}

		context("match_user 행이 없는 사용자가 조회하면") {
			it("같은 회사 소개 거부 플래그가 기본값 거부(true)로 내려온다 (200)") {
				val userId: Long = 5003L
				IntegrationUtil.persist(
					UserDetailEntity(
						userId = userId,
						nickname = "기본거부유저",
					),
				)

				get("/users/v1/profile") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data.refuseSameCompanyIntro", true)
				}
			}
		}

		context("같은 회사 소개 거부를 해제한 사용자가 조회하면") {
			it("같은 회사 소개 거부 플래그가 false로 내려온다 (200)") {
				val userId: Long = 5004L
				IntegrationUtil.persist(
					UserDetailEntity(
						userId = userId,
						nickname = "거부해제유저",
					),
				)
				IntegrationUtil.persist(
					MatchUserEntityFixture.create(
						userId = userId,
						refuseSameCompanyIntro = false,
					),
				)

				get("/users/v1/profile") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data.refuseSameCompanyIntro", false)
				}
			}
		}

		context("학교 인증을 마친 사용자가 조회하면") {
			it("학교 이메일·학교명이 응답에 내려온다 (200)") {
				val userId: Long = 5002L
				IntegrationUtil.persist(
					UserDetailEntity(
						userId = userId,
						nickname = "학교유저",
						universityEmail = "student@snu.ac.kr",
						universityName = "서울대학교",
					),
				)

				get("/users/v1/profile") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data.universityEmail", "student@snu.ac.kr")
					body("data.universityName", "서울대학교")
				}
			}
		}
	}

	afterTest {
		cleanupOnboarding()
	}
})
