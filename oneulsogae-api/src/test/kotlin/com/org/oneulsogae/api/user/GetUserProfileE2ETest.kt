package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.user.command.entity.UserDetailEntity
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.nullValue

/**
 * `GET /users/v1/profile/{userId}` E2E 테스트.
 * 다른 사용자의 공개 프로필을 조회하며, **연락처(휴대폰·이메일 3종)와 본인 편집 전용값이 응답에 실리지 않는지**를 함께 검증한다.
 */
class GetUserProfileE2ETest : AbstractIntegrationSupport({

	describe("GET /users/v1/profile/{userId}") {

		context("다른 사용자의 프로필을 조회하면") {
			it("공개 항목만 내려주고 연락처·편집 전용값은 싣지 않는다 (200)") {
				val viewerId: Long = 5101L
				val targetId: Long = 5102L
				IntegrationUtil.persist(
					UserDetailEntity(
						userId = targetId,
						nickname = "공개유저",
						gender = Gender.FEMALE,
						height = 165,
						job = "디자이너",
						phoneNumber = "01012345678",
						companyEmail = "target@company.com",
						companyName = "오늘소개",
						universityEmail = "target@univ.ac.kr",
						universityName = "오늘대학교",
						secondaryEmail = "target@personal.com",
						introduction = "안녕하세요",
						traits = listOf("운동", "독서"),
						interests = listOf("영화", "여행"),
					),
				)

				get("/users/v1/profile/$targetId") {
					bearer(accessTokenFor(viewerId))
				} expect {
					status(200)
					body("success", true)
					body("data.userId", targetId.toInt())
					body("data.nickname", "공개유저")
					body("data.gender", Gender.FEMALE.name)
					body("data.height", 165)
					body("data.job", "디자이너")
					body("data.companyName", "오늘소개")
					body("data.universityName", "오늘대학교")
					body("data.introduction", "안녕하세요")
					body("data.traits", contains("운동", "독서"))
					body("data.interests", contains("영화", "여행"))
					// 연락처는 프로필 열람만으로 노출하지 않는다. (본인 프로필 조회에만 실린다)
					body("data.phoneNumber", nullValue())
					body("data.companyEmail", nullValue())
					body("data.universityEmail", nullValue())
					body("data.secondaryEmail", nullValue())
					// 본인 편집 화면 전용값도 남에게는 의미가 없어 제외한다.
					body("data.regionId", nullValue())
					body("data.refuseSameCompanyIntro", nullValue())
				}
			}
		}

		context("프로필이 없는 사용자를 조회하면") {
			it("404를 반환한다") {
				val viewerId: Long = 5103L

				get("/users/v1/profile/99999999") {
					bearer(accessTokenFor(viewerId))
				} expect {
					status(404)
					body("success", false)
				}
			}
		}

		context("선택 옵션 경로는") {
			it("userId 경로 변수에 가로채이지 않고 그대로 동작한다 (200)") {
				val viewerId: Long = 5104L

				get("/users/v1/profile/options") {
					bearer(accessTokenFor(viewerId))
				} expect {
					status(200)
					body("success", true)
				}
			}
		}
	}
})
