package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.integration.post
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.RegionEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.match.command.entity.MatchUserEntity
import com.org.meeple.infra.match.command.entity.QSoloMatchEntity
import com.org.meeple.infra.match.command.entity.QSoloMatchMemberEntity
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.infra.user.command.entity.UserDetailEntity
import com.org.meeple.scheduler.match.command.application.port.out.RegionProximityPort
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 매칭 읽기 모델(match_user) 동기화·소비 E2E.
 *
 * - **동기화**: 정식 가입(ACTIVE) 전이 시(회사명 직접 입력 경로) user 도메인 이벤트로 match_user에 적재되는지.
 * - **소비**: 온보딩 직후 추천(`GET /matches/v1?isAfterOnboarding=true`)이 user_details 조인이 아니라
 *   match_user에서 반대 성별·같은 권역·최근 로그인 후보를 골라 소개를 생성하는지.
 */
class MatchUserSyncE2ETest(
	private val regionProximityPort: RegionProximityPort,
) : AbstractIntegrationSupport({

	describe("match_user 동기화") {

		context("프로필이 완성된 사용자가 회사명 입력으로 정식 가입하면") {
			it("매칭 읽기 모델(match_user)에 기준 필드가 적재된다") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(
						providerId = "sync-active",
						status = UserStatus.COMPANY_NOT_RESOLVED,
						lastLoginAt = LocalDateTime.now(),
					),
				).id!!
				// 매칭 필수 필드(성별·활동지역·결혼여부·생년월일·닉네임·프로필이미지코드)가 모두 채워진 완성 프로필
				IntegrationUtil.persist(
					UserDetailEntity(
						userId = userId,
						nickname = "민수",
						profileImageCode = "1",
						gender = Gender.MALE,
						birthday = LocalDate.of(1995, 1, 1),
						regionId = 7L,
						maritalStatus = MaritalStatus.SINGLE,
					),
				)

				post("/users/v1/onboarding/company-name") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"companyName": "미플"}""")
				} expect {
					status(200)
					body("success", true)
				}

				val row: MatchUserEntity? = IntegrationUtil.getQuery()
					.selectFrom(QMatchUserEntity.matchUserEntity)
					.where(QMatchUserEntity.matchUserEntity.userId.eq(userId))
					.fetchOne()

				row!!.gender shouldBe Gender.MALE
				row.maritalStatus shouldBe MaritalStatus.SINGLE
				row.nickname shouldBe "민수"
				row.profileImageCode shouldBe "1"
			}
		}
	}

	describe("GET /matches/v1?isAfterOnboarding=true") {

		context("match_user에 반대 성별·같은 권역·최근 로그인 후보가 있으면") {
			it("그 후보로 소개(매칭)를 생성해 목록에 내려준다") {
				val regionId: Long = IntegrationUtil.persist(RegionEntityFixture.create()).id!!

				// 요청자(남성, 권역 1): 추천 대상이자 목록 조회 주체. user/user_details + 매칭 읽기 모델을 갖춘다.
				val meUserId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "me-requester", status = UserStatus.ACTIVE),
				).id!!
				IntegrationUtil.persist(
					UserDetailEntity(userId = meUserId, nickname = "철수", gender = Gender.MALE, birthday = LocalDate.of(1996, 1, 1)),
				)
				IntegrationUtil.persist(
					MatchUserEntityFixture.create(userId = meUserId, gender = Gender.MALE, regionId = regionId),
				)

				// 후보(여성, 권역 1, 최근 로그인): match_user에만 있으면 후보로 선정된다. (표시 조인용 user_details도 준비)
				val candidateUserId = 9100L
				IntegrationUtil.persist(
					MatchUserEntityFixture.create(
						userId = candidateUserId,
						gender = Gender.FEMALE,
						regionId = regionId,
						lastLoginAt = LocalDateTime.now(),
					),
				)
				IntegrationUtil.persist(
					UserDetailEntity(userId = candidateUserId, nickname = "영희", gender = Gender.FEMALE, birthday = LocalDate.of(1999, 1, 1)),
				)

				// 매칭 유저를 모두 적재한 뒤 지역 매칭 스냅샷(근접·유저분포)을 갱신한다. (그래야 지역 단위 조회 경로가 후보 지역을 본다)
				regionProximityPort.refresh()

				get("/matches/v1?isAfterOnboarding=true") {
					bearer(accessTokenFor(meUserId))
				} expect {
					status(200)
					body("success", true)
					body("data.size()", 1)
					body("data[0].partner.userId", candidateUserId.toInt())
					body("data[0].partner.nickname", "영희")
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QSoloMatchMemberEntity.soloMatchMemberEntity)
		IntegrationUtil.deleteAll(QSoloMatchEntity.soloMatchEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
		IntegrationUtil.deleteAll(QRegionEntity.regionEntity)
	}
})
