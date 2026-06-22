package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.delete
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.integration.post
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
import java.time.LocalDate

/**
 * `GET /teams/v1/invitation` E2E 테스트. (내가 보낸 초대 현황 조회)
 * 요청자가 ACTIVE 구성원(=초대자)인 가장 최근 INVITING 팀을 구성원 프로필과 함께 반환한다.
 * 초대받은 사람·비구성원·철회된 경우는 data=null(200)로 노출되지 않는다.
 */
class GetSentInvitationE2ETest : AbstractIntegrationSupport({

	// 표시용 프로필을 match_user(닉네임·프로필이미지·나이) + user_details(직업·회사명)에 저장한다. (성별은 MALE 고정)
	fun persistProfile(userId: Long, nickname: String, profileImageCode: String, job: String?, companyName: String?, birthday: LocalDate = LocalDate.of(1998, 1, 1)) {
		IntegrationUtil.persist(
			MatchUserEntityFixture.create(userId = userId, gender = Gender.MALE, nickname = nickname, profileImageCode = profileImageCode, birthday = birthday),
		)
		IntegrationUtil.persist(
			UserDetailEntityFixture.create(
				userId = userId,
				nickname = nickname,
				gender = Gender.MALE,
				profileImageCode = profileImageCode,
				job = job,
				companyName = companyName,
			),
		)
	}

	// 프로필이 저장된 두 사용자로 팀을 결성(초대)하고 teamId를 돌려준다.
	fun invite(ownerId: Long, invitedUserId: Long): Long =
		post("/teams/v1/invitation") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
		}.extract().path<Int>("data.teamId").toLong()

	// 기본 프로필을 저장한 뒤 초대한다. (프로필 값 검증이 필요 없는 케이스용)
	fun setUpInvite(ownerId: Long, invitedUserId: Long): Long {
		persistProfile(ownerId, "초대자$ownerId", "1", null, null)
		persistProfile(invitedUserId, "초대대상$invitedUserId", "1", null, null)
		return invite(ownerId, invitedUserId)
	}

	describe("GET /teams/v1/invitation") {

		context("초대자가 자신이 보낸 초대를 조회하면") {
			it("INVITING 팀이면 초대 대상(INVITED)만 반환하고 초대자 본인(ACTIVE)은 제외한다 (200)") {
				val ownerId = 3001L
				val invitedUserId = 3002L
				persistProfile(ownerId, "초대왕", "10", "PM", "토스")
				persistProfile(invitedUserId, "피초대", "20", "개발자", "카카오", birthday = LocalDate.now().minusYears(30))
				val teamId: Long = invite(ownerId, invitedUserId)

				get("/teams/v1/invitation") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(200)
					body("success", true)
					body("data.teamId", teamId.toInt())
					body("data.name", "우리팀")
					body("data.introduction", "함께 즐겁게 활동할 팀이에요")
					body("data.status", TeamStatus.INVITING.name)
					// INVITING 팀이라 초대자 본인(3001, ACTIVE)은 제외되고 초대 대상(3002, INVITED)만 노출된다
					body("data.members", hasSize<Any>(1))
					body("data.members[0].userId", invitedUserId.toInt())
					body("data.members[0].nickname", "피초대")
					body("data.members[0].job", "개발자")
					body("data.members[0].companyName", "카카오")
					body("data.members[0].gender", Gender.MALE.name)
					body("data.members[0].profileImageCode", "20")
					body("data.members[0].age", 30)
					body("data.members[0].status", TeamMemberStatus.INVITED.name)
				}
			}
		}

		context("초대가 수락되어 팀이 ACTIVE가 된 뒤 초대자가 조회하면") {
			it("ACTIVE 상태와 함께 전원(ACTIVE) 구성원을 반환한다 (200)") {
				val ownerId = 3010L
				val invitedUserId = 3011L
				persistProfile(ownerId, "초대왕", "10", "PM", "토스")
				persistProfile(invitedUserId, "합류자", "20", "개발자", "카카오", birthday = LocalDate.now().minusYears(28))
				val teamId: Long = invite(ownerId, invitedUserId)

				// 초대받은 사용자가 수락 → 전원 ACTIVE라 팀이 ACTIVE로 전이한다.
				post("/teams/v1/$teamId/acceptance") {
					bearer(accessTokenFor(invitedUserId))
				} expect {
					status(200)
				}

				get("/teams/v1/invitation") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(200)
					body("data.teamId", teamId.toInt())
					body("data.status", TeamStatus.ACTIVE.name)
					// 요청자 본인 포함 전원(ACTIVE) 노출. userId 오름차순.
					body("data.members", hasSize<Any>(2))
					body("data.members[0].userId", ownerId.toInt())
					body("data.members[0].status", TeamMemberStatus.ACTIVE.name)
					body("data.members[1].userId", invitedUserId.toInt())
					body("data.members[1].nickname", "합류자")
					body("data.members[1].age", 28)
					body("data.members[1].status", TeamMemberStatus.ACTIVE.name)
				}
			}
		}

		context("직업·회사명이 미입력(null)인 구성원이 있어도") {
			it("해당 필드를 null로 담아 반환한다 (200)") {
				val ownerId = 3008L
				val invitedUserId = 3009L
				persistProfile(ownerId, "널잡", "1", null, null)
				persistProfile(invitedUserId, "널회사", "1", null, null)
				invite(ownerId, invitedUserId)

				get("/teams/v1/invitation") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(200)
					body("data.members[0].job", nullValue())
					body("data.members[0].companyName", nullValue())
				}
			}
		}

		context("초대받은 유저가 조회하면") {
			it("data가 null이다 (200)") {
				val ownerId = 3003L
				val invitedUserId = 3004L
				setUpInvite(ownerId, invitedUserId)

				get("/teams/v1/invitation") {
					bearer(accessTokenFor(invitedUserId))
				} expect {
					status(200)
					body("success", true)
					body("data", nullValue())
				}
			}
		}

		context("진행 중인 초대가 없는 유저가 조회하면") {
			it("data가 null이다 (200)") {
				val userId = 3005L
				persistProfile(userId, "외톨이", "1", null, null)

				get("/teams/v1/invitation") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data", nullValue())
				}
			}
		}

		context("초대를 철회한 뒤 초대자가 조회하면") {
			it("data가 null이다 (200)") {
				val ownerId = 3006L
				val invitedUserId = 3007L
				val teamId: Long = setUpInvite(ownerId, invitedUserId)

				delete("/teams/v1/$teamId/invitation") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(200)
				}

				get("/teams/v1/invitation") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(200)
					body("success", true)
					body("data", nullValue())
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				get("/teams/v1/invitation") expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
	}
})
