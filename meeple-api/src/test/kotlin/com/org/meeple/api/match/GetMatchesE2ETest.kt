package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.UserStatus
import com.org.meeple.core.match.command.domain.MatchMembers
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchEntityFixture
import com.org.meeple.infra.fixture.MatchMemberEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.match.command.entity.MatchEntity
import com.org.meeple.infra.match.command.entity.QMatchEntity
import com.org.meeple.infra.match.command.entity.QMatchMemberEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.infra.user.command.entity.UserDetailEntity
import org.hamcrest.Matchers.contains

/**
 * `GET /matches/v1` E2E 테스트.
 *
 * 매칭 목록 조회가 QueryDSL 투영으로 [com.org.meeple.core.match.query.dto.MatchWithPartner]를 바로 만들 때:
 * - 관심 여부(hasUserInterest/hasPartnerInterest)가 참가자 accepted(nullable)에서 `coalesce(false)`로 올바로 산출되는지,
 * - `@Convert`(JSON) 컬럼인 상대 프로필 traits/interests가 컨버터를 거쳐 `List<String>`으로 복원되는지 검증한다.
 */
class GetMatchesE2ETest : AbstractIntegrationSupport({

	describe("GET /matches/v1") {

		context("상대만 관심을 보낸(내 미응답) 매칭이 있으면") {
			it("관심 플래그가 accepted에서 산출되고 상대 프로필의 JSON 컬럼이 리스트로 내려온다 (200)") {
				// 요청자는 User+UserDetail 조인이 필요하다. (성별로 매칭 대상 판단)
				val meUserId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
				).id!!
				IntegrationUtil.persist(
					UserDetailEntity(userId = meUserId, nickname = "철수", gender = Gender.MALE, age = 30),
				)

				// 상대는 dao 조인 대상(match_members + user_details)만 있으면 된다.
				val partnerUserId = 9001L
				IntegrationUtil.persist(
					UserDetailEntity(
						userId = partnerUserId,
						nickname = "영희",
						gender = Gender.FEMALE,
						age = 27,
						traits = listOf("요가", "등산"),
						interests = listOf("재즈", "미술"),
					),
				)

				val match: MatchEntity = IntegrationUtil.persist(
					MatchEntityFixture.create(
						memberKey = MatchMembers.memberKeyOf(listOf(meUserId, partnerUserId)),
						status = MatchStatus.PARTIALLY_ACCEPTED,
					),
				)
				val matchId: Long = match.id!!
				// 나: 미응답(null) → hasUserInterest=false
				IntegrationUtil.persist(
					MatchMemberEntityFixture.create(matchId = matchId, userId = meUserId, gender = Gender.MALE, accepted = null),
				)
				// 상대: 수락(true) → hasPartnerInterest=true
				IntegrationUtil.persist(
					MatchMemberEntityFixture.create(matchId = matchId, userId = partnerUserId, gender = Gender.FEMALE, accepted = true),
				)

				get("/matches/v1") {
					bearer(accessTokenFor(meUserId))
				} expect {
					status(200)
					body("success", true)
					body("data.size()", 1)
					body("data[0].matchId", matchId.toInt())
					body("data[0].hasUserInterest", false)
					body("data[0].hasPartnerInterest", true)
					body("data[0].partner.userId", partnerUserId.toInt())
					body("data[0].partner.nickname", "영희")
					body("data[0].partner.traits", contains("요가", "등산"))
					body("data[0].partner.interests", contains("재즈", "미술"))
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QMatchMemberEntity.matchMemberEntity)
		IntegrationUtil.deleteAll(QMatchEntity.matchEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
