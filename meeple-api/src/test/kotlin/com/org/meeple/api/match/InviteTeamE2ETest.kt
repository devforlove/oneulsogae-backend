package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.match.command.entity.TeamEntity
import com.org.meeple.infra.match.command.entity.TeamMemberEntity
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * `POST /teams/v1` E2E 테스트. (팀 초대/결성 엔드포인트)
 *
 * 초대자(인증 사용자)가 다른 사용자를 초대해 팀을 결성한다. 초대 대상은 즉시 구성원으로 합류하고 팀은 초대중(INVITING) 상태가 된다.
 * 구성원 성별은 match 도메인 소유 읽기 모델(match_user)에서 읽으므로, 두 사용자의 match_user 행을 준비한다. (행이 없으면 매칭 불가)
 * 실제 서버(RANDOM_PORT) + Testcontainers(MySQL/Redis)를 기동하고 HTTP를 호출한다.
 */
class InviteTeamE2ETest : AbstractIntegrationSupport({

	// 매칭 읽기 모델(match_user) 행을 저장한다. (성별 조회 대상)
	fun persistMatchUser(userId: Long, gender: Gender) {
		IntegrationUtil.persist(
			MatchUserEntityFixture.create(userId = userId, gender = gender),
		)
	}

	describe("POST /teams/v1") {

		context("초대자가 같은 성별의 다른 사용자를 초대하면") {
			it("두 사람을 구성원으로 담은 초대중(INVITING) 팀이 결성된다 (200)") {
				val ownerId = 1001L
				val invitedUserId = 1002L
				persistMatchUser(ownerId, Gender.MALE)
				persistMatchUser(invitedUserId, Gender.MALE)

				post("/teams/v1") {
					bearer(accessTokenFor(ownerId))
					jsonBody(
						"""{"invitedUserId": $invitedUserId, "name": "우리팀", "introduction": "잘 부탁드려요"}""",
					)
				} expect {
					status(200)
					body("success", true)
					body("data.name", "우리팀")
					body("data.introduction", "잘 부탁드려요")
					body("data.status", TeamStatus.INVITING.name)
					body("data.memberUserIds.size()", 2)
				}

				// 팀 헤더 1건이 INVITING으로 저장된다.
				val teams: List<TeamEntity> = allTeams()
				teams.size shouldBe 1
				teams[0].name shouldBe "우리팀"
				teams[0].status shouldBe TeamStatus.INVITING
				// 구성원 두 행(초대자 + 초대 대상)이 저장된다.
				val members: List<TeamMemberEntity> = teamMembersOf(teams[0].id!!)
				members.map { it.userId } shouldContainExactlyInAnyOrder listOf(ownerId, invitedUserId)
			}
		}

		context("자기 자신을 초대하면") {
			it("400(TEAM-001)을 반환하고 팀이 만들어지지 않는다") {
				val ownerId = 1001L
				persistMatchUser(ownerId, Gender.MALE)

				post("/teams/v1") {
					bearer(accessTokenFor(ownerId))
					jsonBody("""{"invitedUserId": $ownerId, "name": "우리팀", "introduction": null}""")
				} expect {
					status(400)
					body("success", false)
					body("error.code", "TEAM-001")
				}

				allTeams().size shouldBe 0
			}
		}

		context("초대 대상이 다른 성별이면") {
			it("400(TEAM-004)을 반환하고 팀이 만들어지지 않는다") {
				val ownerId = 1001L
				val invitedUserId = 1002L
				persistMatchUser(ownerId, Gender.MALE)
				persistMatchUser(invitedUserId, Gender.FEMALE)

				post("/teams/v1") {
					bearer(accessTokenFor(ownerId))
					jsonBody("""{"invitedUserId": $invitedUserId, "name": "우리팀", "introduction": null}""")
				} expect {
					status(400)
					body("success", false)
					body("error.code", "TEAM-004")
				}

				allTeams().size shouldBe 0
			}
		}

		context("초대 대상이 매칭 가능 상태(match_user)가 아니면") {
			it("400(MATCH-005)을 반환하고 팀이 만들어지지 않는다") {
				val ownerId = 1001L
				val invitedUserId = 9999L // match_user 행 없음
				persistMatchUser(ownerId, Gender.MALE)

				post("/teams/v1") {
					bearer(accessTokenFor(ownerId))
					jsonBody("""{"invitedUserId": $invitedUserId, "name": "우리팀", "introduction": null}""")
				} expect {
					status(400)
					body("success", false)
					body("error.code", "MATCH-005")
				}

				allTeams().size shouldBe 0
			}
		}

		context("팀 이름이 비어 있으면") {
			it("400을 반환한다 (요청 검증)") {
				val ownerId = 1001L
				val invitedUserId = 1002L
				persistMatchUser(ownerId, Gender.MALE)
				persistMatchUser(invitedUserId, Gender.MALE)

				post("/teams/v1") {
					bearer(accessTokenFor(ownerId))
					jsonBody("""{"invitedUserId": $invitedUserId, "name": "  ", "introduction": null}""")
				} expect {
					status(400)
					body("success", false)
				}

				allTeams().size shouldBe 0
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				post("/teams/v1") {
					jsonBody("""{"invitedUserId": 2, "name": "우리팀", "introduction": null}""")
				} expect {
					status(401)
				}
			}
		}

		context("초대자(owner)가 이미 활성 팀에 속해 있으면") {
			it("409(TEAM-009)을 반환한다") {
				val ownerId = 1001L
				val invited1 = 1002L
				val invited2 = 1003L
				persistMatchUser(ownerId, Gender.MALE)
				persistMatchUser(invited1, Gender.MALE)
				persistMatchUser(invited2, Gender.MALE)

				// 첫 팀 결성(owner는 활성 구성원이 됨)
				post("/teams/v1") {
					bearer(accessTokenFor(ownerId))
					jsonBody("""{"invitedUserId": $invited1, "name": "팀1", "introduction": null}""")
				} expect { status(200) }

				// 같은 owner가 또 초대 → 한 팀만 위반
				post("/teams/v1") {
					bearer(accessTokenFor(ownerId))
					jsonBody("""{"invitedUserId": $invited2, "name": "팀2", "introduction": null}""")
				} expect {
					status(409)
					body("error.code", "TEAM-009")
				}
			}
		}

		context("초대 대상이 이미 활성 팀에 속해 있으면") {
			it("409(TEAM-009)을 반환한다") {
				val owner1 = 1001L
				val owner2 = 1004L
				val invitedUserId = 1002L
				persistMatchUser(owner1, Gender.MALE)
				persistMatchUser(owner2, Gender.MALE)
				persistMatchUser(invitedUserId, Gender.MALE)

				// invitedUserId가 owner1 팀의 구성원이 됨
				post("/teams/v1") {
					bearer(accessTokenFor(owner1))
					jsonBody("""{"invitedUserId": $invitedUserId, "name": "팀1", "introduction": null}""")
				} expect { status(200) }

				// owner2가 같은 사람을 초대 → 한 팀만 위반
				post("/teams/v1") {
					bearer(accessTokenFor(owner2))
					jsonBody("""{"invitedUserId": $invitedUserId, "name": "팀2", "introduction": null}""")
				} expect {
					status(409)
					body("error.code", "TEAM-009")
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
	}
})

// 저장된 팀 전체. (조회는 리포지토리 대신 IntegrationUtil.getQuery()(QueryDSL)로 수행한다)
private fun allTeams(): List<TeamEntity> {
	val team: QTeamEntity = QTeamEntity.teamEntity
	return IntegrationUtil.getQuery().selectFrom(team).fetch()
}

// 한 팀의 구성원 행 전체.
private fun teamMembersOf(teamId: Long): List<TeamMemberEntity> {
	val member: QTeamMemberEntity = QTeamMemberEntity.teamMemberEntity
	return IntegrationUtil.getQuery()
		.selectFrom(member)
		.where(member.teamId.eq(teamId))
		.fetch()
}
