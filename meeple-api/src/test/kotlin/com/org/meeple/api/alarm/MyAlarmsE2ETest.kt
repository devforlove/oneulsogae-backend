package com.org.meeple.api.alarm

import com.org.meeple.common.alarm.AlarmType
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.alarm.command.entity.QAlarmEntity
import com.org.meeple.infra.fixture.AlarmEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.match.command.entity.TeamMemberEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import java.time.LocalDateTime

/**
 * `GET /alarms/v1` E2E 테스트.
 *
 * 현재 로그인 사용자의 알람을 최신순(id 내림차순)으로 조회하고, 알람을 유발한 발신 유저 프로필을
 * 정규화된 `from` 배열(fromUserId IN 조회)로 함께 반환한다.
 * 실제 서버(RANDOM_PORT) + Testcontainers(MySQL)를 기동하고 HTTP를 호출한다.
 * 데이터 준비/정리는 [IntegrationUtil], 요청/검증은 [get]/[expect] Kotlin DSL로 한다.
 */
class MyAlarmsE2ETest : AbstractIntegrationSupport({

	describe("GET /alarms/v1") {

		context("최근 1개월 이내/이전 알람과 타인 알람이 섞여 있으면") {
			it("본인의 최근 1개월 이내 알람만 최신순으로, 각 알람에 발신자 프로필(froms)을 담아 반환한다") {
				val userId = 7101L
				val now: LocalDateTime = LocalDateTime.now()

				// 발신 유저 프로필. 살아남는 알람의 발신자(8102/8101)는 froms에 채워지고, 발신자 없는 알람은 froms가 빈 배열.
				persistUserDetail(userId = 8102L, profileImageCode = "M03", gender = Gender.MALE)
				persistUserDetail(userId = 8101L, profileImageCode = "F07", gender = Gender.FEMALE)

				// 2일 전(최신), 20일 전, 40일 전(1개월 초과 → 제외), 타인 알람 → 제외
				val recentId: Long = persistAlarmAt(userId, "2일전 알람", fromUserId = 8102L, createdAt = now.minusDays(2))
				val olderId: Long = persistAlarmAt(userId, "20일전 알람", fromUserId = 8101L, createdAt = now.minusDays(20))
				persistAlarmAt(userId, "40일전 알람", fromUserId = 8100L, createdAt = now.minusDays(40))
				persistAlarmAt(9101L, "남의 알람", fromUserId = null, createdAt = now.minusDays(1))

				get("/alarms/v1") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					// 1개월 초과(40일 전)·타인 알람은 빠지고 2건만
					body("data.size()", 2)
					// 생성 시각 최신순: 2일 전 → 20일 전
					body("data[0].id", recentId.toInt())
					body("data[0].title", "2일전 알람")
					body("data[0].type", AlarmType.ONE_TO_ONE_INTEREST_RECEIVED.name)
					body("data[0].description", "회원님에게 관심을 보낸 상대가 있어요.")
					body("data[0].link", "/")
					body("data[0].fromUserId", 8102)
					body("data[0].isRead", false)
					body("data[0].createdAt", notNullValue())
					body("data[1].id", olderId.toInt())
					body("data[1].title", "20일전 알람")
					// froms: 각 알람의 발신자 프로필. (발신자 fromUserId에 해당하는 프로필 1건)
					body("data[0].froms.size()", 1)
					body("data[0].froms[0].userId", 8102)
					body("data[0].froms[0].profileImageCode", "M03")
					body("data[0].froms[0].gender", Gender.MALE.name)
					body("data[1].froms.size()", 1)
					body("data[1].froms[0].userId", 8101)
					body("data[1].froms[0].profileImageCode", "F07")
					body("data[1].froms[0].gender", Gender.FEMALE.name)
				}
			}
		}

		context("발신 팀(fromTeamId)이 있는 알람이면") {
			it("그 팀 구성원들의 프로필을 froms에 담아 반환한다") {
				val userId = 7104L
				val teamId = 8800L
				// 발신(상대) 팀 구성원 2인과 그 프로필. froms는 이 둘로 채워진다.
				persistTeamMember(teamId = teamId, userId = 8801L)
				persistTeamMember(teamId = teamId, userId = 8802L)
				persistUserDetail(userId = 8801L, profileImageCode = "M01", gender = Gender.MALE)
				persistUserDetail(userId = 8802L, profileImageCode = "M02", gender = Gender.MALE)

				persistAlarmAt(userId, "팀 관심 알람", fromTeamId = teamId, createdAt = LocalDateTime.now().minusDays(1))

				get("/alarms/v1") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data.size()", 1)
					body("data[0].fromUserId", nullValue())
					body("data[0].fromTeamId", teamId.toInt())
					// froms: 발신 팀 구성원 2인의 프로필 (순서 무관)
					body("data[0].froms.size()", 2)
					body("data[0].froms.userId", containsInAnyOrder(8801, 8802))
				}
			}
		}

		context("발신 팀이 해체되어 구성원이 소프트 삭제됐어도") {
			it("소프트 삭제된 구성원 프로필까지 froms에 담아 반환한다") {
				val userId = 7105L
				val teamId = 8810L
				// 해체로 소프트 삭제된 발신 팀 구성원 2인 + 프로필. team_members가 deleted_at으로 가려져도 froms에 채워져야 한다.
				persistDeletedTeamMember(teamId = teamId, userId = 8811L)
				persistDeletedTeamMember(teamId = teamId, userId = 8812L)
				persistUserDetail(userId = 8811L, profileImageCode = "M04", gender = Gender.MALE)
				persistUserDetail(userId = 8812L, profileImageCode = "M05", gender = Gender.MALE)

				persistAlarmAt(userId, "팀 매칭 종료 알람", fromTeamId = teamId, createdAt = LocalDateTime.now().minusDays(1))

				get("/alarms/v1") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data.size()", 1)
					body("data[0].fromTeamId", teamId.toInt())
					// 소프트 삭제된 구성원 2인의 프로필이 그대로 채워진다.
					body("data[0].froms.size()", 2)
					body("data[0].froms.userId", containsInAnyOrder(8811, 8812))
				}
			}
		}

		context("발신자(fromUserId)가 없는 알람이면") {
			it("froms가 빈 배열로 반환된다") {
				val userId = 7103L
				persistAlarmAt(userId, "시스템 알람", fromUserId = null, createdAt = LocalDateTime.now().minusDays(1))

				get("/alarms/v1") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data.size()", 1)
					body("data[0].fromUserId", nullValue())
					body("data[0].froms.size()", 0)
				}
			}
		}

		context("알람이 없으면") {
			it("빈 목록을 반환한다") {
				get("/alarms/v1") {
					bearer(accessTokenFor(7102L))
				} expect {
					status(200)
					body("success", true)
					body("data.size()", 0)
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				get("/alarms/v1") {} expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
	}
})

// 발신 유저 프로필(user_details)을 저장한다. from 배열 조회(IN 쿼리) 검증용.
private fun persistUserDetail(userId: Long, profileImageCode: String, gender: Gender) {
	IntegrationUtil.persist(
		UserDetailEntityFixture.create(userId = userId, profileImageCode = profileImageCode, gender = gender),
	)
}

// 발신 팀 구성원(team_members) 한 행을 저장한다. fromTeamId 알람의 froms(팀 구성원 IN 조회) 검증용.
private fun persistTeamMember(teamId: Long, userId: Long) {
	IntegrationUtil.persist(
		TeamMemberEntity(teamId = teamId, userId = userId, status = TeamMemberStatus.ACTIVE),
	)
}

// 해체로 소프트 삭제된 팀 구성원 행을 저장한다. (종료된 팀의 발신 알람 froms 조회 검증용 — @SQLRestriction에 가려지는 행)
private fun persistDeletedTeamMember(teamId: Long, userId: Long) {
	val member = TeamMemberEntity(teamId = teamId, userId = userId, status = TeamMemberStatus.DEACTIVE)
	member.softDelete(LocalDateTime.of(2026, 1, 1, 0, 0))
	IntegrationUtil.persist(member)
}

// 알람을 저장한 뒤 생성 시각(created_at)을 원하는 값으로 백데이트하고 id를 반환한다.
// created_at은 JPA Auditing이 저장 시 now로 채우므로, 1개월 컷오프/정렬 검증을 위해 QueryDSL 업데이트로 덮어쓴다.
private fun persistAlarmAt(
	userId: Long,
	title: String,
	fromUserId: Long? = null,
	fromTeamId: Long? = null,
	createdAt: LocalDateTime,
): Long {
	val saved = IntegrationUtil.persist(
		AlarmEntityFixture.create(userId = userId, title = title, fromUserId = fromUserId, fromTeamId = fromTeamId),
	)
	val id: Long = saved.id!!
	val alarm: QAlarmEntity = QAlarmEntity.alarmEntity
	IntegrationUtil.update { query ->
		query.update(alarm)
			.set(alarm.createdAt, createdAt)
			.where(alarm.id.eq(id))
			.execute()
	}
	return id
}
