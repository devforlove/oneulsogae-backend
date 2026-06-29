package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.integration.post
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.fixture.CompanyEmailVerificationEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.RegionEntityFixture
import com.org.meeple.infra.fixture.UserCompanyEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.matchuser.command.entity.MatchUserEntity
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchEntity
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchMemberEntity
import com.org.meeple.infra.matchuser.command.entity.QMatchUserEntity
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.user.command.entity.QCompanyEmailVerificationEntity
import com.org.meeple.infra.user.command.entity.QUserCompanyEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.infra.user.command.entity.UserDetailEntity
import com.org.meeple.scheduler.common.command.application.port.out.RegionProximityPort
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 매칭 읽기 모델(match_user) 동기화·소비 E2E.
 *
 * - **동기화**: 정식 가입(ACTIVE) 전이 시(회사명 직접 입력 경로) user 도메인 이벤트로 match_user에 적재되는지.
 * - **소비**: 회사 이메일 인증으로 온보딩이 완료되면, match_user에서 반대 성별·같은 권역·최근 로그인 후보를 골라
 *   첫 소개(매칭)를 자동 생성하는지. (조회 경로가 아니라 인증 완료 시점에 생성 — CQS 분리)
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

	describe("회사 이메일 인증으로 온보딩이 완료되면") {

		context("match_user에 반대 성별·같은 권역·최근 로그인 후보가 있으면") {
			it("인증 완료 시점에 그 후보로 첫 매칭(소개)을 자동 생성해 목록에 내려준다") {
				val regionId: Long = IntegrationUtil.persist(RegionEntityFixture.create()).id!!

				// 요청자: 온보딩 중(ONBOARDING) + 매칭 필수 필드를 갖춘 완성 프로필 + 회사 도메인 매핑 + 대기 중 인증번호.
				// 인증이 완료되면 ACTIVE로 전환되어 match_user가 적재되고(BEFORE_COMMIT), 커밋 직후 첫 소개가 생성된다(AFTER_COMMIT).
				val meUserId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "verify-me", status = UserStatus.ONBOARDING, lastLoginAt = LocalDateTime.now()),
				).id!!
				IntegrationUtil.persist(
					UserDetailEntity(
						userId = meUserId,
						nickname = "철수",
						profileImageCode = "1",
						gender = Gender.MALE,
						birthday = LocalDate.of(1996, 1, 1),
						regionId = regionId,
						maritalStatus = MaritalStatus.SINGLE,
					),
				)
				IntegrationUtil.persist(
					CompanyEmailVerificationEntityFixture.create(userId = meUserId, companyEmail = "me@meeple.com", code = "123456"),
				)
				IntegrationUtil.persist(UserCompanyEntityFixture.create(emailDomain = "meeple.com", companyName = "미플"))

				// 후보(여성, 같은 권역, 최근 로그인): match_user에만 있으면 후보로 선정된다. (표시 조인용 user_details도 준비)
				val candidateUserId = 9100L
				IntegrationUtil.persist(
					MatchUserEntityFixture.create(userId = candidateUserId, gender = Gender.FEMALE, regionId = regionId, lastLoginAt = LocalDateTime.now()),
				)
				IntegrationUtil.persist(
					UserDetailEntity(userId = candidateUserId, nickname = "영희", gender = Gender.FEMALE, birthday = LocalDate.of(1999, 1, 1)),
				)

				// 후보 match_user 적재 후 지역 근접 스냅샷을 갱신한다. (요청자 match_user는 인증 완료 시점에 적재된다)
				regionProximityPort.refresh()

				post("/users/v1/onboarding/company-email/verifications/confirm") {
					bearer(accessTokenFor(meUserId))
					jsonBody("""{"code": "123456"}""")
				} expect {
					status(200)
					body("success", true)
				}

				// 인증 완료 직후 자동 소개가 생성돼, 매칭 목록 조회에 후보가 내려온다. (GET 자체는 부수효과 없음)
				get("/matches/v1") {
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
		IntegrationUtil.deleteAll(QCompanyEmailVerificationEntity.companyEmailVerificationEntity)
		IntegrationUtil.deleteAll(QUserCompanyEntity.userCompanyEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
		IntegrationUtil.deleteAll(QRegionEntity.regionEntity)
	}
})
