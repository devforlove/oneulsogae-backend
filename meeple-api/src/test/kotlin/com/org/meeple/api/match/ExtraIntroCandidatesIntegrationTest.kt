package com.org.meeple.api.match

import com.org.meeple.common.coin.CoinUsageType
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.solomatch.command.domain.MatchMembers
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.SoloMatchEntityFixture
import com.org.meeple.infra.fixture.SoloMatchMemberEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.matchuser.command.entity.QMatchUserEntity
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchEntity
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchMemberEntity
import com.org.meeple.infra.solomatch.command.entity.SoloMatchEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import io.kotest.matchers.collections.shouldNotContain
import io.restassured.response.ValidatableResponse
import java.time.LocalDateTime

/**
 * `GET /matches/v1/extra/candidates` E2E 테스트.
 *
 * 추가 소개 자격 후보 조회가 (a) 상위 11명만 표시하고 전체 자격 후보 수를 함께 내려주는지,
 * (b) 같은 성별·최근 로그인 아님·이미 소개된 상대를 후보에서 제외하는지, (c) 매칭 불가(match_user 없음) 요청자는 빈 결과인지 검증한다.
 * 선택은 무작위 셔플·거리 점수(E2E에선 빈 근접 스냅샷으로 0)를 쓰므로 순서는 단언하지 않고, 개수·전체 수·제외 여부만 단언한다.
 */
class ExtraIntroCandidatesIntegrationTest : AbstractIntegrationSupport({

	val now: LocalDateTime = LocalDateTime.now()

	// 요청자(매칭 가능): match_user만 있으면 자격 후보 조회가 동작한다. (요청자 프로필은 스코어링용이라 없어도 됨)
	fun persistRequester(userId: Long, gender: Gender = Gender.MALE) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	// 자격 후보: 후보 조회가 match_user(성별·최근 로그인) + user_details 명시 조인을 요구하므로 둘 다 저장한다.
	fun persistCandidate(userId: Long, gender: Gender = Gender.FEMALE, lastLoginAt: LocalDateTime = now): Long {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender, lastLoginAt = lastLoginAt))
		IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, gender = gender))
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

	// 응답의 후보 userId 목록을 추출한다. (제외 여부 검증용)
	fun candidateUserIds(response: ValidatableResponse): List<Int> =
		response.extract().path("data.candidates.userId")

	describe("GET /matches/v1/extra/candidates") {

		context("반대 성별 자격 후보가 12명 이상이면") {
			it("상위 11명만 표시하고 전체 자격 후보 수·추가 소개 코인 비용을 반환한다") {
				val requesterId = 1L
				persistRequester(requesterId)
				(1..12).forEach { i: Int -> persistCandidate(1000L + i) }

				get("/matches/v1/extra/candidates") {
					bearer(accessTokenFor(requesterId))
				} expect {
					status(200)
					body("success", true)
					body("data.candidates.size()", 11)
					body("data.totalCount", 12)
					body("data.coinCost", CoinUsageType.EXTRA_INTRO.coinAmount)
				}
			}
		}

		context("같은 성별 후보는") {
			it("전체 수·목록에서 제외된다") {
				val requesterId = 1L
				persistRequester(requesterId)
				persistCandidate(1001L)
				persistCandidate(1002L)
				persistCandidate(1003L)
				val sameGenderId: Long = persistCandidate(2001L, gender = Gender.MALE)

				val response: ValidatableResponse = get("/matches/v1/extra/candidates") {
					bearer(accessTokenFor(requesterId))
				}
				response expect {
					status(200)
					body("data.totalCount", 3)
					body("data.candidates.size()", 3)
				}
				candidateUserIds(response) shouldNotContain sameGenderId.toInt()
			}
		}

		context("최근 로그인이 2주를 넘은 후보는") {
			it("전체 수·목록에서 제외된다") {
				val requesterId = 1L
				persistRequester(requesterId)
				persistCandidate(1001L)
				persistCandidate(1002L)
				persistCandidate(1003L)
				val staleId: Long = persistCandidate(2001L, lastLoginAt = now.minusWeeks(3))

				val response: ValidatableResponse = get("/matches/v1/extra/candidates") {
					bearer(accessTokenFor(requesterId))
				}
				response expect {
					status(200)
					body("data.totalCount", 3)
					body("data.candidates.size()", 3)
				}
				candidateUserIds(response) shouldNotContain staleId.toInt()
			}
		}

		context("이미 요청자와 소개된 상대는") {
			it("전체 수·목록에서 제외된다") {
				val requesterId = 1L
				persistRequester(requesterId)
				persistCandidate(1001L)
				persistCandidate(1002L)
				persistCandidate(1003L)
				val introducedId: Long = persistCandidate(2001L)
				persistIntroduced(requesterId, introducedId)

				val response: ValidatableResponse = get("/matches/v1/extra/candidates") {
					bearer(accessTokenFor(requesterId))
				}
				response expect {
					status(200)
					body("data.totalCount", 3)
					body("data.candidates.size()", 3)
				}
				candidateUserIds(response) shouldNotContain introducedId.toInt()
			}
		}

		context("요청자가 매칭 가능 상태가 아니면(match_user 없음)") {
			it("전체 수 0·빈 목록을 반환한다") {
				val requesterId = 99L
				// 요청자 match_user는 만들지 않는다. 후보만 있어도 매칭 불가라 조회 결과는 비어야 한다.
				persistCandidate(1001L)
				persistCandidate(1002L)

				get("/matches/v1/extra/candidates") {
					bearer(accessTokenFor(requesterId))
				} expect {
					status(200)
					body("success", true)
					body("data.totalCount", 0)
					body("data.candidates.size()", 0)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QSoloMatchMemberEntity.soloMatchMemberEntity)
		IntegrationUtil.deleteAll(QSoloMatchEntity.soloMatchEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
	}
})
