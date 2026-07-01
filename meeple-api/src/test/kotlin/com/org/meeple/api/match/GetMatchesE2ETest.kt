package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.match.MatchMemberStatus
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.UserStatus
import com.org.meeple.core.solomatch.command.domain.MatchMembers
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.SoloMatchEntityFixture
import com.org.meeple.infra.fixture.SoloMatchMemberEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.solomatch.command.entity.SoloMatchEntity
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchEntity
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchMemberEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.infra.user.command.entity.UserDetailEntity
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.notNullValue
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * `GET /matches/v1` E2E 테스트.
 *
 * 매칭 목록 조회가 QueryDSL 투영으로 [com.org.meeple.core.solomatch.query.dto.MatchWithPartner]를 바로 만들 때:
 * - 관심 여부(hasUserInterest/hasPartnerInterest)가 참가자 status(APPLY/ACTIVE)에서 올바로 산출되는지,
 * - `@Convert`(JSON) 컬럼인 상대 프로필 traits/interests가 컨버터를 거쳐 `List<String>`으로 복원되는지 검증한다.
 */
class GetMatchesE2ETest : AbstractIntegrationSupport({

	describe("GET /matches/v1") {

		context("상대만 관심을 보낸(내 미응답) 매칭이 있으면") {
			it("관심 플래그가 status에서 산출되고 상대 프로필의 JSON 컬럼이 리스트로 내려온다 (200)") {
				// 요청자는 User+UserDetail 조인이 필요하다. (성별로 매칭 대상 판단)
				val meUserId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
				).id!!
				IntegrationUtil.persist(
					UserDetailEntity(userId = meUserId, nickname = "철수", gender = Gender.MALE, birthday = LocalDate.of(1996, 1, 1)),
				)

				// 상대는 dao 조인 대상(solo_match_members + user_details)이 필요하고, lastLoginAt은 users에서 온다.
				val partnerLastLoginAt: LocalDateTime = LocalDateTime.of(2026, 6, 30, 9, 0, 0)
				val partnerUserId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "partner-provider-id", status = UserStatus.ACTIVE, lastLoginAt = partnerLastLoginAt),
				).id!!
				IntegrationUtil.persist(
					UserDetailEntity(
						userId = partnerUserId,
						nickname = "영희",
						gender = Gender.FEMALE,
						birthday = LocalDate.of(1999, 1, 1),
						traits = listOf("요가", "등산"),
						interests = listOf("재즈", "미술"),
					),
				)

				val match: SoloMatchEntity = IntegrationUtil.persist(
					SoloMatchEntityFixture.create(
						memberKey = MatchMembers.memberKeyOf(listOf(meUserId, partnerUserId)),
						status = MatchStatus.PARTIALLY_ACCEPTED,
					),
				)
				val matchId: Long = match.id!!
				// 나: 미신청(WAITING) → hasUserInterest=false
				IntegrationUtil.persist(
					SoloMatchMemberEntityFixture.create(matchId = matchId, userId = meUserId, gender = Gender.MALE, status = MatchMemberStatus.WAITING),
				)
				// 상대: 신청(APPLY) → hasPartnerInterest=true
				IntegrationUtil.persist(
					SoloMatchMemberEntityFixture.create(matchId = matchId, userId = partnerUserId, gender = Gender.FEMALE, status = MatchMemberStatus.APPLY),
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
					body("data[0].partner.lastLoginAt", notNullValue())
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QSoloMatchMemberEntity.soloMatchMemberEntity)
		IntegrationUtil.deleteAll(QSoloMatchEntity.soloMatchEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
