package com.org.meeple.api.match

import com.org.meeple.common.alarm.AlarmType
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.alarm.command.entity.AlarmEntity
import com.org.meeple.infra.alarm.command.entity.QAlarmEntity
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.match.command.entity.TeamMemberEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import io.kotest.matchers.shouldBe

/**
 * `POST /teams/v1/{teamId}/acceptance` E2E 테스트. (초대 수락)
 * 초대받은 사용자가 수락하면 본인이 ACTIVE가 되고 전원 ACTIVE이므로 팀이 ACTIVE로 전이한다.
 */
class AcceptTeamInvitationE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	// 팀을 결성(초대)하고 teamId를 돌려준다.
	fun inviteTeam(ownerId: Long, invitedUserId: Long): Long {
		persistMatchUser(ownerId, Gender.MALE)
		persistMatchUser(invitedUserId, Gender.MALE)
		return post("/teams/v1/invitation") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
		}.extract().path<Int>("data.teamId").toLong()
	}

	// 이미 영속된 ownerId/invitedUserId로 초대만 보낸다. (사용자 재영속 없이 같은 invitedUserId에게 여러 초대 전송용)
	fun invite(ownerId: Long, invitedUserId: Long): Long =
		post("/teams/v1/invitation") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
		}.extract().path<Int>("data.teamId").toLong()

	describe("POST /teams/v1/{teamId}/acceptance") {

		context("초대받은 사용자가 수락하면") {
			it("본인이 ACTIVE가 되고 팀이 ACTIVE가 된다 (200)") {
				val ownerId = 2001L
				val invitedUserId = 2002L
				val teamId: Long = inviteTeam(ownerId, invitedUserId)

				post("/teams/v1/$teamId/acceptance") {
					bearer(accessTokenFor(invitedUserId))
				} expect {
					status(200)
					body("success", true)
					body("data.status", TeamStatus.ACTIVE.name)
				}

				val members: List<TeamMemberEntity> = teamMembersOf(teamId)
				members.all { it.status == TeamMemberStatus.ACTIVE } shouldBe true
			}
		}

		context("초대받은 사용자가 수락하면 (알람)") {
			it("초대했던 사람에게만 '초대 수락됨' 알람이 저장된다 (200)") {
				val ownerId = 2009L
				val invitedUserId = 2010L
				val teamId: Long = inviteTeam(ownerId, invitedUserId)
				// 초대 단계에서 생긴 '초대 받음' 알람은 이 테스트의 관심사가 아니므로 초기화한다.
				IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
				// 수락 알람 문구엔 수락한 사람(초대받은 사람) 닉네임이 들어간다.
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = invitedUserId, gender = Gender.MALE, nickname = "영희"),
				)

				post("/teams/v1/$teamId/acceptance") {
					bearer(accessTokenFor(invitedUserId))
				} expect {
					status(200)
					body("success", true)
				}

				// 초대자에게만 수락 알람.
				val alarms: List<AlarmEntity> = alarmsOf(ownerId)
				alarms.size shouldBe 1
				val alarm: AlarmEntity = alarms[0]
				alarm.type shouldBe AlarmType.TEAM_INVITATION_ACCEPTED
				alarm.fromUserId shouldBe invitedUserId
				alarm.fromTeamId shouldBe teamId
				alarm.description shouldBe "영희님이 팀 초대를 수락했어요."
				alarm.link shouldBe "/friend/team"
				alarmsOf(invitedUserId).size shouldBe 0
			}
		}

		context("같은 사용자가 받은 초대가 여러 개일 때 하나를 수락하면") {
			it("수락한 팀 외에 받은 다른 초대들은 모두 비활성화된다 (200)") {
				val invitedUserId = 2006L
				val owner1 = 2007L
				val owner2 = 2008L
				persistMatchUser(invitedUserId, Gender.MALE)
				persistMatchUser(owner1, Gender.MALE)
				persistMatchUser(owner2, Gender.MALE)
				val acceptedTeamId: Long = invite(owner1, invitedUserId)
				val otherTeamId: Long = invite(owner2, invitedUserId)

				post("/teams/v1/$acceptedTeamId/acceptance") {
					bearer(accessTokenFor(invitedUserId))
				} expect {
					status(200)
					body("data.status", TeamStatus.ACTIVE.name)
				}

				teamMembersOf(acceptedTeamId).all { it.status == TeamMemberStatus.ACTIVE } shouldBe true
				// 다른 초대는 팀이 소프트 삭제되어 활성 조회에서 사라진다. (@SQLRestriction)
				teamMembersOf(otherTeamId).isEmpty() shouldBe true
			}
		}

		context("수락하는 사용자가 owner로 만든 다른 INVITING 팀이 있을 때 수락하면") {
			it("내가 owner로 만든 INVITING 팀도 함께 비활성화된다 (200)") {
				val userX = 2011L
				val userY = 2012L
				val userZ = 2013L
				persistMatchUser(userX, Gender.MALE)
				persistMatchUser(userY, Gender.MALE)
				persistMatchUser(userZ, Gender.MALE)

				// userZ가 userX를 초대(T2, userX=INVITED). userX가 ACTIVE 소속이 되기 전에 초대해 둔다.
				val acceptedTeamId: Long = invite(userZ, userX)
				// userX가 userY를 초대해 자기 팀 생성(T1, userX=ACTIVE owner).
				val myInvitingTeamId: Long = invite(userX, userY)

				// userX가 T2 초대를 수락.
				post("/teams/v1/$acceptedTeamId/acceptance") {
					bearer(accessTokenFor(userX))
				} expect {
					status(200)
					body("data.status", TeamStatus.ACTIVE.name)
				}

				teamMembersOf(acceptedTeamId).all { it.status == TeamMemberStatus.ACTIVE } shouldBe true
				// userX가 owner로 만든 INVITING 팀(T1)도 비활성화되어 활성 조회에서 사라진다.
				teamMembersOf(myInvitingTeamId).isEmpty() shouldBe true
			}
		}

		context("초대받지 않은(이미 ACTIVE인) owner가 수락하면") {
			it("400(TEAM-007)을 반환한다") {
				val ownerId = 2003L
				val invitedUserId = 2004L
				val teamId: Long = inviteTeam(ownerId, invitedUserId)

				post("/teams/v1/$teamId/acceptance") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(400)
					body("error.code", "TEAM-007")
				}
			}
		}

		context("없는 팀을 수락하면") {
			it("404(TEAM-005)를 반환한다") {
				val userId = 2005L
				persistMatchUser(userId, Gender.MALE)

				post("/teams/v1/999999/acceptance") {
					bearer(accessTokenFor(userId))
				} expect {
					status(404)
					body("error.code", "TEAM-005")
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
	}
})

private fun teamMembersOf(teamId: Long): List<TeamMemberEntity> {
	val member: QTeamMemberEntity = QTeamMemberEntity.teamMemberEntity
	return IntegrationUtil.getQuery().selectFrom(member).where(member.teamId.eq(teamId)).fetch()
}

// 한 사용자에게 저장된 알람 전체.
private fun alarmsOf(userId: Long): List<AlarmEntity> {
	val alarm: QAlarmEntity = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery().selectFrom(alarm).where(alarm.userId.eq(userId)).fetch()
}
