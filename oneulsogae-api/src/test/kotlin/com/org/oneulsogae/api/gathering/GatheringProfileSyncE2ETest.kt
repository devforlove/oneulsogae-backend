package com.org.oneulsogae.api.gathering

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.put
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.infra.fixture.GatheringProfileEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.RegionEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.gathering.command.entity.GatheringProfileEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringProfileEntity
import com.org.oneulsogae.infra.matchuser.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.region.entity.QRegionEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import com.org.oneulsogae.infra.user.command.entity.UserDetailEntity
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 유저 프로필 변경(UserProfileChanged) 시 gathering_profile의 유저 유래 필드(프로필이미지·생일·키)가
 * 최신 user_details 값으로 동기화되는지 검증한다. (직종·직장상세는 어드민 확정값이라 유지)
 * 트리거로는 프로필 수정(PUT /users/v1/profile)을 쓴다(UserProfileChanged를 발행).
 */
class GatheringProfileSyncE2ETest : AbstractIntegrationSupport({

	fun gatheringProfileByUserId(userId: Long): GatheringProfileEntity {
		val p: QGatheringProfileEntity = QGatheringProfileEntity.gatheringProfileEntity
		return IntegrationUtil.getQuery().selectFrom(p).where(p.userId.eq(userId)).fetchOne()!!
	}

	describe("프로필 변경 시 gathering_profile 동기화") {

		it("유저 유래 필드(프로필이미지·생일·키)를 최신 값으로 맞추고, 직종·직장상세는 유지한다") {
			val regionId: Long = IntegrationUtil.persist(RegionEntityFixture.create()).id!!
			val userId: Long = IntegrationUtil.persist(
				UserEntityFixture.create(providerId = "gp-sync", status = UserStatus.ACTIVE, lastLoginAt = LocalDateTime.now()),
			).id!!
			// 현재 프로필: 프로필이미지 "1", 키 180, 생일 1995-01-01. (키·생일은 프로필 수정으로 못 바꾸는 보존 필드)
			IntegrationUtil.persist(
				UserDetailEntity(
					userId = userId,
					nickname = "민수",
					profileImageCode = "1",
					gender = Gender.MALE,
					birthday = LocalDate.of(1995, 1, 1),
					height = 180,
					regionId = regionId,
					maritalStatus = MaritalStatus.SINGLE,
				),
			)
			// 승인 시점 스냅샷(낡음): 프로필이미지 "old_img", 키 170, 생일 1990-01-01. 직종·직장상세는 어드민 확정값.
			IntegrationUtil.persist(
				GatheringProfileEntityFixture.create(
					userId = userId,
					jobCategory = "IT·개발직",
					jobDetail = "오늘의 소개 백엔드 개발자",
					birthday = LocalDate.of(1990, 1, 1),
					height = 170,
					profileImageCode = "old_img",
				),
			)

			// 프로필 이미지 코드를 "9"로 수정 → UserProfileChanged 발행 → gathering_profile 동기화.
			put("/users/v1/profile") {
				bearer(accessTokenFor(userId))
				jsonBody(
					"""
					{
					  "nickname": "민수",
					  "profileImageCode": "9",
					  "job": "개발자",
					  "regionId": $regionId,
					  "introduction": "안녕하세요 잘 부탁드립니다.",
					  "traits": ["성실함"],
					  "interests": ["영화"],
					  "maritalStatus": "SINGLE",
					  "smokingStatus": "NON_SMOKER",
					  "religion": "NONE",
					  "drinkingStatus": "SOMETIMES",
					  "bodyType": "MALE_NORMAL"
					}
					""".trimIndent(),
				)
			} expect {
				status(200)
				body("success", true)
			}

			val profile: GatheringProfileEntity = gatheringProfileByUserId(userId)
			// 유저 유래 필드는 최신 user_details 값으로 동기화.
			profile.profileImageCode shouldBe "9"
			profile.height shouldBe 180
			profile.birthday shouldBe LocalDate.of(1995, 1, 1)
			// 어드민 확정값은 유지.
			profile.jobCategory shouldBe "IT·개발직"
			profile.jobDetail shouldBe "오늘의 소개 백엔드 개발자"
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QGatheringProfileEntity.gatheringProfileEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
		IntegrationUtil.deleteAll(QRegionEntity.regionEntity)
	}
})
