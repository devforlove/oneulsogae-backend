package com.org.oneulsogae.api.match

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.core.solomatch.command.domain.MatchMembers
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinHistoryEntity
import com.org.oneulsogae.infra.fixture.CoinBalanceEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MatchUserEntityFixture
import com.org.oneulsogae.infra.fixture.SoloMatchEntityFixture
import com.org.oneulsogae.infra.fixture.SoloMatchMemberEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.matchuser.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.solomatch.command.entity.QSoloMatchEntity
import com.org.oneulsogae.infra.solomatch.command.entity.QSoloMatchMemberEntity
import com.org.oneulsogae.infra.solomatch.command.entity.SoloMatchEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import io.restassured.response.ValidatableResponse
import org.hamcrest.Matchers.greaterThan

/**
 * `POST /matches/v1/extra` E2E 테스트. (추가 소개 받기)
 *
 * 코인([com.org.oneulsogae.common.coin.CoinUsageType.EXTRA_INTRO]=30)을 차감하고 자격 후보 1명을 골라
 * [com.org.oneulsogae.common.match.SoloMatchType.EXTRA] PROPOSED 매칭을 만든다. 후보가 없거나 코인이 부족하면 매칭·차감이 없다.
 * 선택은 무작위 셔플·거리 점수(빈 근접 스냅샷 0)를 쓰므로 어느 후보가 뽑히는지는 단언하지 않고,
 * 상대가 자격 후보 집합에 속하는지·코인 증감·매칭 생성/미생성만 단언한다.
 * 실제 서버(RANDOM_PORT) + Testcontainers(MySQL/Redis, 분산 락 포함)를 기동하고 HTTP를 호출한다.
 */
class ExtraIntroIntegrationTest : AbstractIntegrationSupport({

	// 요청자(match_user만) + 코인 잔액을 저장한다. (POST /extra는 match_user·코인만 있으면 동작)
	fun persistRequester(userId: Long, balance: Int) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = Gender.MALE))
		IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = userId, balance = balance))
	}

	// 자격 후보: 후보 조회가 match_user + user_details를 요구하므로 둘 다 저장한다.
	fun persistCandidate(userId: Long): Long {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = Gender.FEMALE))
		IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, gender = Gender.FEMALE))
		return userId
	}

	// 요청자와 후보가 과거에 소개된 이력(참가자 조합 키)을 만든다. (재소개 제외 대상)
	fun persistIntroduced(requesterId: Long, candidateId: Long) {
		val match: SoloMatchEntity = IntegrationUtil.persist(
			SoloMatchEntityFixture.create(
				memberKey = MatchMembers.memberKeyOf(listOf(requesterId, candidateId)),
				status = MatchStatus.PROPOSED,
			),
		)
		IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = match.id!!, userId = requesterId, gender = Gender.MALE))
		IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = match.id!!, userId = candidateId, gender = Gender.FEMALE))
	}

	describe("POST /matches/v1/extra") {

		context("코인이 충분하고 자격 후보가 있으면") {
			it("코인 30을 차감하고 자격 후보 1명과 EXTRA 매칭을 만들며, 그 매칭이 목록에 노출된다 (200)") {
				// 목록 조회(GET /matches/v1)는 요청자 user+user_details 조인이 필요하다. match_user.userId는 이 id와 맞춘다.
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(status = UserStatus.ACTIVE)).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = requesterId, gender = Gender.MALE, companyName = "오늘소개"))
				persistRequester(requesterId, balance = 100)
				val candidateIds: List<Long> = listOf(persistCandidate(1001L), persistCandidate(1002L), persistCandidate(1003L))

				val response: ValidatableResponse = post("/matches/v1/extra") {
					bearer(accessTokenFor(requesterId))
				}
				response expect {
					status(200)
					body("success", true)
					body("data.matchId", greaterThan(0))
				}
				val matchId: Int = response.extract().path("data.matchId")
				val partnerUserId: Int = response.extract().path("data.partnerUserId")
				partnerUserId.toLong() shouldBeIn candidateIds
				// 추가 소개 코인(EXTRA_INTRO=30) 차감 → 잔액 70
				coinBalanceOf(requesterId) shouldBe 70

				// 생성된 매칭이 내 매칭 목록에 노출된다. (상대 = 방금 뽑힌 후보)
				get("/matches/v1") {
					bearer(accessTokenFor(requesterId))
				} expect {
					status(200)
					body("data.matches.size()", 1)
					body("data.matches[0].matchId", matchId)
					body("data.matches[0].partner.userId", partnerUserId)
				}
			}
		}

		context("자격 후보가 없으면") {
			it("EXTRA_INTRO_NO_CANDIDATE로 실패하고 코인이 그대로다 (404)") {
				val requesterId = 1L
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = requesterId, gender = Gender.MALE, companyName = "오늘소개"))
				persistRequester(requesterId, balance = 100)
				// 반대 성별 후보 없음 (자격 후보 0)

				post("/matches/v1/extra") {
					bearer(accessTokenFor(requesterId))
				} expect {
					status(404)
					body("success", false)
					body("error.code", "MATCH-010")
				}

				coinBalanceOf(requesterId) shouldBe 100
				matchCountInvolving(requesterId) shouldBe 0
			}
		}

		context("자격 후보가 이미 소개된 1명뿐이면") {
			it("재소개 제외로 후보 없음 처리되어 실패하고 코인이 그대로다 (404)") {
				val requesterId = 1L
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = requesterId, gender = Gender.MALE, companyName = "오늘소개"))
				persistRequester(requesterId, balance = 100)
				val introducedId: Long = persistCandidate(1001L)
				persistIntroduced(requesterId, introducedId)

				post("/matches/v1/extra") {
					bearer(accessTokenFor(requesterId))
				} expect {
					status(404)
					body("error.code", "MATCH-010")
				}

				coinBalanceOf(requesterId) shouldBe 100
			}
		}

		context("코인이 부족하면") {
			it("실패하고 코인·매칭이 그대로다 (400, COIN-001)") {
				val requesterId = 1L
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = requesterId, gender = Gender.MALE, companyName = "오늘소개"))
				persistRequester(requesterId, balance = 10) // 30보다 적음
				persistCandidate(1001L)

				post("/matches/v1/extra") {
					bearer(accessTokenFor(requesterId))
				} expect {
					status(400)
					body("success", false)
					body("error.code", "COIN-001")
				}

				// 차감 실패로 트랜잭션이 롤백되어 잔액 유지 + 매칭 미생성
				coinBalanceOf(requesterId) shouldBe 10
				matchCountInvolving(requesterId) shouldBe 0
			}
		}

		context("요청자가 회사 인증을 마치지 않았으면") {
			it("403(USER-035)을 반환하고 코인이 차감되지 않는다") {
				val requesterId = 1L
				persistRequester(requesterId, balance = 100)
				// 회사명이 없는 프로필 = 회사 인증 미완료
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = requesterId, gender = Gender.MALE))
				persistCandidate(1001L)

				post("/matches/v1/extra") {
					bearer(accessTokenFor(requesterId))
				} expect {
					status(403)
					body("success", false)
					body("error.code", "USER-035")
				}

				// 차단이 코인 차감보다 앞이라 잔액이 그대로다.
				coinBalanceOf(requesterId) shouldBe 100
				matchCountInvolving(requesterId) shouldBe 0
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				post("/matches/v1/extra") {} expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QSoloMatchMemberEntity.soloMatchMemberEntity)
		IntegrationUtil.deleteAll(QSoloMatchEntity.soloMatchEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
		IntegrationUtil.deleteAll(QCoinHistoryEntity.coinHistoryEntity)
	}
})

// 조회는 리포지토리 대신 IntegrationUtil.getQuery()(QueryDSL)로 수행한다. 스칼라 프로젝션으로 DB 최신값을 읽는다.
private fun coinBalanceOf(userId: Long): Int {
	val coinBalance: QCoinBalanceEntity = QCoinBalanceEntity.coinBalanceEntity
	return IntegrationUtil.getQuery()
		.select(coinBalance.balance)
		.from(coinBalance)
		.where(coinBalance.userId.eq(userId))
		.fetchOne()!!
}

// 요청자가 참가자로 속한 매칭 수. (매칭 미생성 검증용)
private fun matchCountInvolving(userId: Long): Long {
	val member: QSoloMatchMemberEntity = QSoloMatchMemberEntity.soloMatchMemberEntity
	return IntegrationUtil.getQuery()
		.select(member.count())
		.from(member)
		.where(member.userId.eq(userId))
		.fetchOne()!!
}
