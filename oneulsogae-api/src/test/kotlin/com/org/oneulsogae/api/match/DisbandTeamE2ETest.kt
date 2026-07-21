package com.org.oneulsogae.api.match

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.delete
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
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
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import io.kotest.matchers.shouldBe

/**
 * `DELETE /teams/v1/{teamId}` E2E 테스트. (결성된 팀에서 구성원이 떠나기 — 두 단계)
 * - 1단계(첫 구성원 탈퇴): 남은 팀원이 있어 팀은 DISBANDED로 남고(소프트 삭제 안 함), 남은 팀원에게 알림이 간다.
 * - 2단계(마지막 구성원 탈퇴): 팀이 DEACTIVATED로 소프트 삭제되어 활성 조회에서 사라진다.
 */
class DisbandTeamE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	// 결성(ACTIVE)까지 진행한 팀의 teamId를 돌려준다. (초대 → 수락)
	// 초대·수락 모두 회사 인증이 필요하므로 owner·invited 모두 인증된 프로필을 미리 채운다. (닉네임은 알람 문구 검증용으로 호출부에서 지정 가능)
	fun formedTeam(ownerId: Long, invitedUserId: Long, invitedNickname: String? = null): Long {
		persistMatchUser(ownerId, Gender.MALE)
		persistMatchUser(invitedUserId, Gender.MALE)
		IntegrationUtil.persist(UserDetailEntityFixture.create(userId = ownerId, gender = Gender.MALE, companyName = "오늘소개"))
		IntegrationUtil.persist(
			UserDetailEntityFixture.create(
				userId = invitedUserId,
				gender = Gender.MALE,
				companyName = "오늘소개",
				nickname = invitedNickname ?: "테스트유저",
			),
		)
		val teamId: Long = post("/teams/v1/invitation") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "regionId": 1, "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
		}.extract().path<Int>("data.teamId").toLong()
		post("/teams/v1/$teamId/acceptance") { bearer(accessTokenFor(invitedUserId)) }
		return teamId
	}

	describe("DELETE /teams/v1/{teamId}") {

		context("ACTIVE 팀에서 한 구성원이 떠나면 (1단계)") {
			it("팀이 DISBANDED로 남고(활성 조회 유지), 남은 팀원에게 '팀 해체' 알람이 간다 (200)") {
				val ownerId = 4001L
				val invitedUserId = 4002L
				// 알람 문구에 들어갈 탈퇴 구성원(invited)의 닉네임을 위해 formedTeam에 닉네임을 지정한다.
				val teamId: Long = formedTeam(ownerId, invitedUserId, invitedNickname = "철수")
				// 결성 과정(초대 받음/수락)에서 생긴 알람은 이 테스트의 관심사가 아니므로 초기화한다.
				IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)

				delete("/teams/v1/$teamId") {
					bearer(accessTokenFor(invitedUserId))
				} expect {
					status(200)
					body("success", true)
				}

				// 마지막 구성원이 아니므로 팀은 DISBANDED로 남고 소프트 삭제되지 않는다.
				teamStatusOf(teamId) shouldBe TeamStatus.DISBANDED
				// 떠난 본인(invited) 제외, 남은 팀원(owner)에게만 '팀 해체' 알람. 발신 유저는 떠난 구성원.
				val alarms: List<AlarmEntity> = disbandAlarmsOf(ownerId)
				alarms.size shouldBe 1
				alarms[0].fromUserId shouldBe invitedUserId
				alarms[0].fromTeamId shouldBe null
				alarms[0].description shouldBe "철수님이 팀을 탈퇴했어요."
				disbandAlarmsOf(invitedUserId).size shouldBe 0
			}
		}

		context("DISBANDED 팀에서 마지막 구성원이 떠나면 (2단계)") {
			it("팀이 비활성화(DEACTIVATED)되어 활성 조회에서 사라진다 (200)") {
				val ownerId = 4007L
				val invitedUserId = 4008L
				val teamId: Long = formedTeam(ownerId, invitedUserId)

				// 1단계: invited가 먼저 떠나 DISBANDED.
				delete("/teams/v1/$teamId") { bearer(accessTokenFor(invitedUserId)) } expect { status(200) }
				teamStatusOf(teamId) shouldBe TeamStatus.DISBANDED

				// 2단계: 남은 owner가 마저 떠나면 DEACTIVATED + 소프트 삭제.
				delete("/teams/v1/$teamId") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(200)
					body("success", true)
				}

				// 소프트 삭제되어 활성 조회에서 사라진다.
				teamStatusOf(teamId) shouldBe null
			}
		}

		context("아직 INVITING인 팀에 해체를 호출하면") {
			it("409(TEAM-008)를 반환한다") {
				val ownerId = 4003L
				val invitedUserId = 4004L
				persistMatchUser(ownerId, Gender.MALE)
				persistMatchUser(invitedUserId, Gender.MALE)
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = ownerId, gender = Gender.MALE, companyName = "오늘소개"))
				val teamId: Long = post("/teams/v1/invitation") {
					bearer(accessTokenFor(ownerId))
					jsonBody("""{"invitedUserId": $invitedUserId, "regionId": 1, "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
				}.extract().path<Int>("data.teamId").toLong()

				delete("/teams/v1/$teamId") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(409)
					body("error.code", "TEAM-008")
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

// 소프트 삭제(@SQLRestriction)되지 않은 팀의 상태. 삭제됐으면 null.
private fun teamStatusOf(teamId: Long): TeamStatus? {
	val team: QTeamEntity = QTeamEntity.teamEntity
	return IntegrationUtil.getQuery().select(team.status).from(team).where(team.id.eq(teamId)).fetchOne()
}

private fun disbandAlarmsOf(userId: Long): List<AlarmEntity> {
	val alarm: QAlarmEntity = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery().selectFrom(alarm)
		.where(alarm.userId.eq(userId).and(alarm.type.eq(AlarmType.TEAM_DISBANDED))).fetch()
}
