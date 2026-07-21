package com.org.oneulsogae.api.match

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.delete
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.alarm.command.entity.AlarmEntity
import com.org.oneulsogae.infra.alarm.command.entity.QAlarmEntity
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MatchUserEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.matchuser.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QTeamEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QTeamMemberEntity
import com.org.oneulsogae.infra.teammatch.command.entity.TeamEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import io.kotest.matchers.shouldBe

/**
 * `DELETE /teams/v1/{teamId}/invitation` E2E 테스트. (초대 거절·취소)
 * INVITING 팀을 철회하면 팀이 소프트 삭제되어 활성 조회에서 사라진다.
 */
class WithdrawTeamInvitationE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	// 초대자는 회사 인증이 필요하다. (닉네임은 알람 문구 검증용으로 호출부에서 지정 가능)
	fun inviteTeam(ownerId: Long, invitedUserId: Long, ownerNickname: String = "테스트유저"): Long {
		persistMatchUser(ownerId, Gender.MALE)
		persistMatchUser(invitedUserId, Gender.MALE)
		IntegrationUtil.persist(
			UserDetailEntityFixture.create(userId = ownerId, gender = Gender.MALE, companyName = "오늘소개", nickname = ownerNickname),
		)
		return post("/teams/v1/invitation") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "regionId": 1, "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
		}.extract().path<Int>("data.teamId").toLong()
	}

	describe("DELETE /teams/v1/{teamId}/invitation") {

		context("초대받은 사람이 거절하면") {
			it("팀이 비활성화되고, 초대자에게 '초대 거절됨' 알람이 저장된다 (200)") {
				val ownerId = 3001L
				val invitedUserId = 3002L
				val teamId: Long = inviteTeam(ownerId, invitedUserId)
				// 초대 단계에서 생긴 '초대 받음' 알람은 이 테스트의 관심사가 아니므로 초기화한다.
				IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
				// 거절 알람 문구엔 거절한 사람(초대받은 사람) 닉네임이 들어간다.
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = invitedUserId, gender = Gender.MALE, nickname = "영희"),
				)

				delete("/teams/v1/$teamId/invitation") {
					bearer(accessTokenFor(invitedUserId))
				} expect {
					status(200)
					body("success", true)
				}

				allTeams().size shouldBe 0
				// 초대자에게만 거절 알람.
				val alarms: List<AlarmEntity> = alarmsOf(ownerId)
				alarms.size shouldBe 1
				val alarm: AlarmEntity = alarms[0]
				alarm.type shouldBe AlarmType.TEAM_INVITATION_DECLINED
				alarm.fromUserId shouldBe invitedUserId
				// fromTeamId는 상대 팀 매칭 알림에만 쓰며, 초대 알림은 발신 유저(fromUserId)만 둔다.
				alarm.fromTeamId shouldBe null
				alarm.description shouldBe "영희님이 팀 초대를 거절했어요."
				alarm.link shouldBe "/friend/team"
				alarmsOf(invitedUserId).size shouldBe 0
			}
		}

		context("초대자(owner)가 취소하면") {
			it("팀이 비활성화되고, 초대받았던 사람에게 '초대 취소됨' 알람이 저장된다 (200)") {
				val ownerId = 3003L
				val invitedUserId = 3004L
				// 취소 알람 문구엔 취소한 초대자 닉네임이 들어간다.
				val teamId: Long = inviteTeam(ownerId, invitedUserId, ownerNickname = "철수")
				// 초대 단계에서 생긴 '초대 받음' 알람은 이 테스트의 관심사가 아니므로 초기화한다.
				IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)

				delete("/teams/v1/$teamId/invitation") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(200)
				}

				allTeams().size shouldBe 0
				// 초대받았던 사람에게만 취소 알람.
				val alarms: List<AlarmEntity> = alarmsOf(invitedUserId)
				alarms.size shouldBe 1
				val alarm: AlarmEntity = alarms[0]
				alarm.type shouldBe AlarmType.TEAM_INVITATION_CANCELED
				alarm.fromUserId shouldBe ownerId
				// fromTeamId는 상대 팀 매칭 알림에만 쓰며, 초대 알림은 발신 유저(fromUserId)만 둔다.
				alarm.fromTeamId shouldBe null
				alarm.description shouldBe "철수님이 팀 초대를 취소했어요."
				alarm.link shouldBe "/friend/invites"
				alarmsOf(ownerId).size shouldBe 0
			}
		}

		context("구성원이 아닌 사용자가 철회하면") {
			it("403(TEAM-006)을 반환한다") {
				val ownerId = 3005L
				val invitedUserId = 3006L
				val strangerId = 3007L
				val teamId: Long = inviteTeam(ownerId, invitedUserId)
				persistMatchUser(strangerId, Gender.MALE)

				delete("/teams/v1/$teamId/invitation") {
					bearer(accessTokenFor(strangerId))
				} expect {
					status(403)
					body("error.code", "TEAM-006")
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

// 소프트 삭제되지 않은(활성) 팀 전체. @SQLRestriction이 삭제행을 제외한다.
private fun allTeams(): List<TeamEntity> {
	val team: QTeamEntity = QTeamEntity.teamEntity
	return IntegrationUtil.getQuery().selectFrom(team).fetch()
}

// 한 사용자에게 저장된 알람 전체.
private fun alarmsOf(userId: Long): List<AlarmEntity> {
	val alarm: QAlarmEntity = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery().selectFrom(alarm).where(alarm.userId.eq(userId)).fetch()
}
