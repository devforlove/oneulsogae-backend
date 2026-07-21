package com.org.oneulsogae.api.match

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinHistoryEntity
import com.org.oneulsogae.infra.fixture.CompanyEmailVerificationEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MatchUserEntityFixture
import com.org.oneulsogae.infra.fixture.RegionEntityFixture
import com.org.oneulsogae.infra.fixture.UserCompanyEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.matchuser.command.entity.MatchUserEntity
import com.org.oneulsogae.infra.solomatch.command.entity.QSoloMatchEntity
import com.org.oneulsogae.infra.solomatch.command.entity.QSoloMatchMemberEntity
import com.org.oneulsogae.infra.matchuser.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.region.entity.QRegionEntity
import com.org.oneulsogae.infra.user.command.entity.QCompanyEmailVerificationEntity
import com.org.oneulsogae.infra.user.command.entity.QUserCompanyEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import com.org.oneulsogae.infra.user.command.entity.UserDetailEntity
import com.org.oneulsogae.scheduler.common.command.application.port.out.RegionProximityPort
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 매칭 읽기 모델(match_user) 동기화·소비 E2E.
 *
 * - **동기화**: 마이탭 회사 이메일 인증이 확정되면 변경된 프로필(회사명 포함)이 match_user에 반영되는지.
 *   (회사명은 같은 회사 소개 차단 판정에 쓰인다)
 * - **소비**: 온보딩 완료(POST /users/v1/onboarding/complete)로 정식 가입되면, match_user에서
 *   반대 성별·같은 권역·최근 로그인 후보를 골라 첫 소개(매칭)를 자동 생성하는지. (조회 경로가 아니라 가입 시점에 생성 — CQS 분리)
 */
class MatchUserSyncE2ETest(
	private val regionProximityPort: RegionProximityPort,
) : AbstractIntegrationSupport({

	describe("match_user 동기화") {

		context("정식 가입(ACTIVE) 사용자가 마이탭에서 회사 이메일 인증을 마치면") {
			it("매칭 읽기 모델(match_user)에 기준 필드와 회사명이 반영된다") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(
						providerId = "sync-active",
						status = UserStatus.ACTIVE,
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
				IntegrationUtil.persist(
					CompanyEmailVerificationEntityFixture.create(userId = userId, companyEmail = "me@oneulsogae.com", code = "123456"),
				)
				IntegrationUtil.persist(UserCompanyEntityFixture.create(emailDomain = "oneulsogae.com", companyName = "오늘의 소개"))

				post("/users/v1/onboarding/company-email/verifications/confirm") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"code": "123456"}""")
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
				// 인증으로 확정된 회사명이 같은 회사 소개 차단 판정용으로 함께 적재된다.
				row.companyName shouldBe "오늘의 소개"
			}
		}
	}

	describe("온보딩 완료(complete)로 정식 가입되면") {

		context("match_user에 반대 성별·같은 권역·최근 로그인 후보가 있으면") {
			it("가입 완료 시점에 그 후보로 첫 매칭(소개)을 자동 생성해 목록에 내려준다") {
				val regionId: Long = IntegrationUtil.persist(RegionEntityFixture.create()).id!!

				// 요청자: 온보딩 중(ONBOARDING). 프로필은 온보딩 완료 요청 본문으로 입력된다.
				// 완료되면 ACTIVE로 전환되어 match_user가 적재되고, 같은 트랜잭션에서 첫 소개가 생성된다.
				val meUserId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "complete-me", status = UserStatus.ONBOARDING, lastLoginAt = LocalDateTime.now()),
				).id!!

				// 후보(여성, 같은 권역, 최근 로그인): match_user에만 있으면 후보로 선정된다. (표시 조인용 user_details도 준비)
				val candidateUserId = 9100L
				IntegrationUtil.persist(
					MatchUserEntityFixture.create(userId = candidateUserId, gender = Gender.FEMALE, regionId = regionId, lastLoginAt = LocalDateTime.now()),
				)
				IntegrationUtil.persist(
					UserDetailEntity(userId = candidateUserId, nickname = "영희", gender = Gender.FEMALE, birthday = LocalDate.of(1999, 1, 1)),
				)

				// 후보 match_user 적재 후 지역 근접 스냅샷을 갱신한다. (요청자 match_user는 가입 완료 시점에 적재된다)
				regionProximityPort.refresh()

				post("/users/v1/onboarding/complete") {
					bearer(accessTokenFor(meUserId))
					jsonBody(
						"""
						{
						  "nickname": "철수",
						  "birthday": "1996-01-01",
						  "height": 175,
						  "gender": "MALE",
						  "phoneNumber": "010-1234-5678",
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

				// 가입 완료 직후 자동 소개가 생성돼, 매칭 목록 조회에 후보가 내려온다. (GET 자체는 부수효과 없음)
				get("/matches/v1") {
					bearer(accessTokenFor(meUserId))
				} expect {
					status(200)
					body("success", true)
					body("data.matches.size()", 1)
					body("data.matches[0].partner.userId", candidateUserId.toInt())
					body("data.matches[0].partner.nickname", "영희")
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QSoloMatchMemberEntity.soloMatchMemberEntity)
		IntegrationUtil.deleteAll(QSoloMatchEntity.soloMatchEntity)
		IntegrationUtil.deleteAll(QCompanyEmailVerificationEntity.companyEmailVerificationEntity)
		IntegrationUtil.deleteAll(QUserCompanyEntity.userCompanyEntity)
		// 온보딩 완료가 코인 잔액 준비·가입 축하 지급을 수행하므로 함께 정리한다.
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
		IntegrationUtil.deleteAll(QCoinHistoryEntity.coinHistoryEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
		IntegrationUtil.deleteAll(QRegionEntity.regionEntity)
	}
})
