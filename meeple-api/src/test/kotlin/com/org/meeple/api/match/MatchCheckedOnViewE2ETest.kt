package com.org.meeple.api.match

import com.org.meeple.common.alarm.AlarmType
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.match.MatchMemberStatus
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.UserStatus
import com.org.meeple.core.solomatch.command.domain.MatchMembers
import com.org.meeple.infra.alarm.command.entity.QAlarmEntity
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.SoloMatchEntityFixture
import com.org.meeple.infra.fixture.SoloMatchMemberEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchEntity
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchMemberEntity
import com.org.meeple.infra.solomatch.command.entity.SoloMatchMemberEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.infra.user.command.entity.UserDetailEntity
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate

/**
 * 매칭 확인(checked_at) 처리 E2E.
 *
 * `GET /matches/v1` 조회 시 "상대가 관심을 보냈는데 아직 확인 시각이 없는" 매칭은
 * 커밋 후 이벤트([com.org.meeple.core.solomatch.command.domain.event.MatchChecked])로
 * 참가자 확인 시각(solo_match_members.checked_at)이 기록되고, 관심을 보낸 상대에게 "매칭 확인" 알람이 저장되는지 검증한다.
 * (이미 확인된 매칭·상대 관심이 없는 매칭은 기록·알람 없이 그대로다)
 */
class MatchCheckedOnViewE2ETest : AbstractIntegrationSupport({

	describe("GET /matches/v1 - 매칭 확인 처리") {

		context("상대만 관심을 보낸(내 미확인) 매칭을 조회하면") {
			it("내 참가 행에 확인 시각이 기록되고, 상대에게 '매칭 확인' 알람이 저장된다 — 재조회에도 알람은 한 번만") {
				val meUserId: Long = persistUser(providerId = "check-me", nickname = "철수", gender = Gender.MALE)
				val partnerUserId: Long = persistUser(providerId = "check-partner", nickname = "영희", gender = Gender.FEMALE)
				val matchId: Long = persistMatch(meUserId, partnerUserId, MatchStatus.PARTIALLY_ACCEPTED)
				// 나: 미신청(WAITING)·미확인 / 상대: 신청(APPLY) → 조회 시 확인 처리 대상
				persistMember(matchId, meUserId, Gender.MALE, MatchMemberStatus.WAITING)
				persistMember(matchId, partnerUserId, Gender.FEMALE, MatchMemberStatus.APPLY)

				get("/matches/v1") {
					bearer(accessTokenFor(meUserId))
				} expect {
					status(200)
					body("success", true)
				}

				findMember(matchId, meUserId).checkedAt.shouldNotBeNull()
				// 상대(관심을 보낸 사람)에게 "매칭 확인" 알람이 저장된다.
				get("/alarms/v1") {
					bearer(accessTokenFor(partnerUserId))
				} expect {
					status(200)
					body("data.size()", 1)
					body("data[0].type", AlarmType.ONE_TO_ONE_MATCH_CHECKED.name)
					body("data[0].title", "매칭 확인")
					body("data[0].description", "철수님이 매칭을 확인했어요.")
					body("data[0].fromUserId", meUserId.toInt())
				}

				// 다시 조회해도 이미 확인된 매칭이라 알람이 중복 저장되지 않는다.
				get("/matches/v1") {
					bearer(accessTokenFor(meUserId))
				} expect {
					status(200)
				}
				get("/alarms/v1") {
					bearer(accessTokenFor(partnerUserId))
				} expect {
					status(200)
					body("data.size()", 1)
				}
			}
		}

		context("상대가 관심을 보내지 않은(소개만 된) 매칭을 조회하면") {
			it("확인 시각을 기록하지 않고 알람도 저장하지 않는다") {
				val meUserId: Long = persistUser(providerId = "nocheck-me", nickname = "민수", gender = Gender.MALE)
				val partnerUserId: Long = persistUser(providerId = "nocheck-partner", nickname = "지영", gender = Gender.FEMALE)
				val matchId: Long = persistMatch(meUserId, partnerUserId, MatchStatus.PROPOSED)
				persistMember(matchId, meUserId, Gender.MALE, MatchMemberStatus.WAITING)
				persistMember(matchId, partnerUserId, Gender.FEMALE, MatchMemberStatus.WAITING)

				get("/matches/v1") {
					bearer(accessTokenFor(meUserId))
				} expect {
					status(200)
				}

				findMember(matchId, meUserId).checkedAt.shouldBeNull()
				get("/alarms/v1") {
					bearer(accessTokenFor(partnerUserId))
				} expect {
					status(200)
					body("data.size()", 0)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
		IntegrationUtil.deleteAll(QSoloMatchMemberEntity.soloMatchMemberEntity)
		IntegrationUtil.deleteAll(QSoloMatchEntity.soloMatchEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})

// 요청자/상대 공용: users + user_details를 함께 저장한다. (목록 조회는 성별 판단·상대 프로필 조인에 둘 다 필요)
private fun persistUser(providerId: String, nickname: String, gender: Gender): Long {
	val userId: Long = IntegrationUtil.persist(
		UserEntityFixture.create(providerId = providerId, status = UserStatus.ACTIVE),
	).id!!
	IntegrationUtil.persist(
		UserDetailEntity(userId = userId, nickname = nickname, gender = gender, birthday = LocalDate.of(1996, 1, 1)),
	)
	return userId
}

private fun persistMatch(userIdA: Long, userIdB: Long, status: MatchStatus): Long =
	IntegrationUtil.persist(
		SoloMatchEntityFixture.create(
			memberKey = MatchMembers.memberKeyOf(listOf(userIdA, userIdB)),
			status = status,
		),
	).id!!

private fun persistMember(matchId: Long, userId: Long, gender: Gender, status: MatchMemberStatus) {
	IntegrationUtil.persist(
		SoloMatchMemberEntityFixture.create(matchId = matchId, userId = userId, gender = gender, status = status),
	)
}

private fun findMember(matchId: Long, userId: Long): SoloMatchMemberEntity {
	val member: QSoloMatchMemberEntity = QSoloMatchMemberEntity.soloMatchMemberEntity
	return IntegrationUtil.getQuery()
		.selectFrom(member)
		.where(member.matchId.eq(matchId), member.userId.eq(userId))
		.fetchOne()!!
}
