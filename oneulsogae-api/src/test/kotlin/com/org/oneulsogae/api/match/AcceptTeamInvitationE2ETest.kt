package com.org.oneulsogae.api.match

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.match.TeamMemberStatus
import com.org.oneulsogae.common.match.TeamStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.alarm.command.entity.AlarmEntity
import com.org.oneulsogae.infra.alarm.command.entity.QAlarmEntity
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MatchUserEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.matchuser.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QTeamEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QTeamMemberEntity
import com.org.oneulsogae.infra.teammatch.command.entity.TeamMemberEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import io.kotest.matchers.shouldBe

/**
 * `POST /teams/v1/{teamId}/acceptance` E2E 테스트. (초대 수락)
 * 초대받은 사용자가 수락하면 본인이 ACTIVE가 되고 전원 ACTIVE이므로 팀이 ACTIVE로 전이한다.
 */
class AcceptTeamInvitationE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	// 회사 인증을 마친(회사명이 채워진) 프로필. 팀 초대·수락은 회사 인증을 마친 사용자만 할 수 있다.
	fun persistVerifiedDetail(userId: Long) {
		IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, gender = Gender.MALE, companyName = "오늘소개"))
	}

	// 팀을 결성(초대)하고 teamId를 돌려준다. 초대자는 회사 인증이 필요하다.
	fun inviteTeam(ownerId: Long, invitedUserId: Long): Long {
		persistMatchUser(ownerId, Gender.MALE)
		persistMatchUser(invitedUserId, Gender.MALE)
		persistVerifiedDetail(ownerId)
		return post("/teams/v1/invitation") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "regionId": 1, "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
		}.extract().path<Int>("data.teamId").toLong()
	}

	// 이미 영속된 ownerId/invitedUserId로 초대만 보낸다. (사용자 재영속 없이 같은 invitedUserId에게 여러 초대 전송용)
	fun invite(ownerId: Long, invitedUserId: Long): Long =
		post("/teams/v1/invitation") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "regionId": 1, "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
		}.extract().path<Int>("data.teamId").toLong()

	describe("POST /teams/v1/{teamId}/acceptance") {

		context("초대받은 사용자가 수락하면") {
			it("본인이 ACTIVE가 되고 팀이 ACTIVE가 된다 (200)") {
				val ownerId = 2001L
				val invitedUserId = 2002L
				val teamId: Long = inviteTeam(ownerId, invitedUserId)
				persistVerifiedDetail(invitedUserId)

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
				// 수락 알람 문구엔 수락한 사람(초대받은 사람) 닉네임이 들어간다. 회사명도 채워 수락자 회사 인증 게이트를 통과시킨다.
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = invitedUserId, gender = Gender.MALE, nickname = "영희", companyName = "오늘소개"),
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
				// fromTeamId는 상대 팀 매칭 알림에만 쓰며, 초대 알림은 발신 유저(fromUserId)만 둔다.
				alarm.fromTeamId shouldBe null
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
				persistVerifiedDetail(invitedUserId)
				persistVerifiedDetail(owner1)
				persistVerifiedDetail(owner2)
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
				// 자동 거절된 다른 초대의 초대자(owner2)에게 '초대 거절' 알람이 간다.
				alarmsOf(owner2).any { it.type == AlarmType.TEAM_INVITATION_DECLINED } shouldBe true
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
				// userX는 T1의 owner(초대자)이자 T2의 수락자, userZ는 T2의 owner(초대자)라 둘 다 회사 인증이 필요하다.
				persistVerifiedDetail(userX)
				persistVerifiedDetail(userZ)

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
				// 자동 취소된 내 팀(T1)의 초대받은 사람(userY)에게 '초대 취소' 알람이 간다.
				alarmsOf(userY).any { it.type == AlarmType.TEAM_INVITATION_CANCELED } shouldBe true
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
				persistVerifiedDetail(userId)

				post("/teams/v1/999999/acceptance") {
					bearer(accessTokenFor(userId))
				} expect {
					status(404)
					body("error.code", "TEAM-005")
				}
			}
		}

		context("수락자가 회사 인증을 마치지 않았으면") {
			it("403(USER-035)을 반환하고 팀 상태가 그대로다") {
				val ownerId = 2014L
				val invitedUserId = 2015L
				// inviteTeam이 ownerId(초대자)만 인증하므로, invitedUserId는 회사명이 없는(미인증) 상태로 남는다.
				val teamId: Long = inviteTeam(ownerId, invitedUserId)

				post("/teams/v1/$teamId/acceptance") {
					bearer(accessTokenFor(invitedUserId))
				} expect {
					status(403)
					body("success", false)
					body("error.code", "USER-035")
				}

				// 부수효과 없음: 팀은 여전히 INVITING이고, 초대받은 사람도 여전히 INVITED다.
				teamStatusOf(teamId) shouldBe TeamStatus.INVITING
				teamMembersOf(teamId).first { it.userId == invitedUserId }.status shouldBe TeamMemberStatus.INVITED
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

// 소프트 삭제되지 않은(활성) 팀의 상태.
private fun teamStatusOf(teamId: Long): TeamStatus? {
	val team: QTeamEntity = QTeamEntity.teamEntity
	return IntegrationUtil.getQuery().select(team.status).from(team).where(team.id.eq(teamId)).fetchOne()
}

// 한 사용자에게 저장된 알람 전체.
private fun alarmsOf(userId: Long): List<AlarmEntity> {
	val alarm: QAlarmEntity = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery().selectFrom(alarm).where(alarm.userId.eq(userId)).fetch()
}
