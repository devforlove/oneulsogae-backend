package com.org.meeple.api.match

import com.org.meeple.common.alarm.AlarmType
import com.org.meeple.common.chat.ChatRoomStatus
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.command.domain.MatchMembers
import com.org.meeple.infra.alarm.entity.AlarmEntity
import com.org.meeple.infra.alarm.entity.QAlarmEntity
import com.org.meeple.infra.chat.command.entity.QChatRoomEntity
import com.org.meeple.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.meeple.infra.coin.entity.QCoinBalanceEntity
import com.org.meeple.infra.coin.entity.QCoinHistoryEntity
import com.org.meeple.infra.fixture.CoinBalanceEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchEntityFixture
import com.org.meeple.infra.fixture.MatchMemberEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.match.command.entity.MatchEntity
import com.org.meeple.infra.match.command.entity.QMatchEntity
import com.org.meeple.infra.match.command.entity.QMatchMemberEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import io.kotest.matchers.shouldBe

/**
 * `POST /matches/v1/{matchId}/interest` E2E 테스트. (신청/수락 통합 엔드포인트)
 *
 * 상대가 아직 관심을 안 보냈으면 신청(PARTIALLY_ACCEPTED), 이미 보냈으면 수락이 되어 성사(MATCHED)된다.
 * 참가자·수락은 참가자(MatchMember) 행이 진실원천이므로, 매칭 헤더와 참가자 행을 함께 준비한다. (member_key는 참가자 조합과 일치시킨다)
 * 실제 서버(RANDOM_PORT) + Testcontainers(MySQL/Redis, 분산 락 포함)를 기동하고 HTTP를 호출한다.
 */
class SendInterestE2ETest : AbstractIntegrationSupport({

	// 1:1 매칭 헤더 + 두 참가자(남/녀, 수락 여부 포함)를 함께 저장하고 헤더를 반환한다.
	fun persistMatch(
		maleUserId: Long,
		femaleUserId: Long,
		maleAccepted: Boolean? = null,
		femaleAccepted: Boolean? = null,
		status: MatchStatus = MatchStatus.PROPOSED,
	): MatchEntity {
		val match: MatchEntity = IntegrationUtil.persist(
			MatchEntityFixture.create(
				memberKey = MatchMembers.memberKeyOf(listOf(maleUserId, femaleUserId)),
				status = status,
			),
		)
		val matchId: Long = match.id!!
		IntegrationUtil.persist(
			MatchMemberEntityFixture.create(matchId = matchId, userId = maleUserId, gender = Gender.MALE, accepted = maleAccepted),
		)
		IntegrationUtil.persist(
			MatchMemberEntityFixture.create(matchId = matchId, userId = femaleUserId, gender = Gender.FEMALE, accepted = femaleAccepted),
		)
		return match
	}

	describe("POST /matches/v1/{matchId}/interest") {

		context("상대가 아직 관심을 안 보낸 매칭에 관심을 보내면") {
			it("신청 비용이 차감되고 신청이 반영된다 (200, PARTIALLY_ACCEPTED) + 상대에게 '관심 받음' 알람") {
				val maleUserId = 1001L
				val femaleUserId = 2001L
				val match: MatchEntity = persistMatch(maleUserId, femaleUserId)
				// 보낸 사람(남성) 프로필 — 알람 문구에 닉네임이 들어간다.
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = maleUserId, gender = Gender.MALE, nickname = "철수"),
				)
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = maleUserId, balance = 100))

				post("/matches/v1/${match.id}/interest") {
					bearer(accessTokenFor(maleUserId))
				} expect {
					status(200)
					body("success", true)
					body("data.matchStatus", MatchStatus.PARTIALLY_ACCEPTED.name)
				}

				// 신청 비용(DATING_INIT=32) 차감 → 잔액 68
				coinBalanceOf(maleUserId) shouldBe 68
				// 미성사라 채팅방은 아직 생성되지 않는다.
				activeChatRoomCount(maleUserId, femaleUserId) shouldBe 0
				// 상대(여성)에게 "관심 받음" 알람.
				val alarms: List<AlarmEntity> = alarmsOf(femaleUserId)
				alarms.size shouldBe 1
				val alarm: AlarmEntity = alarms[0]
				alarm.type shouldBe AlarmType.INTEREST_RECEIVED
				alarm.fromUserId shouldBe maleUserId
				alarm.description shouldBe "철수님이 회원님에게 관심을 보냈어요."
				alarm.link shouldBe "/"
			}
		}

		context("상대가 이미 관심을 보낸 매칭에 관심을 보내면") {
			it("수락 비용이 차감되고 성사된다 (200, MATCHED) + 채팅방 생성 + 성사된 두 사람 모두에게 '매칭 성사' 알람") {
				val maleUserId = 1002L
				val femaleUserId = 2002L
				// 상대(여성)가 이미 수락한 PARTIALLY_ACCEPTED 매칭
				val match: MatchEntity = persistMatch(
					maleUserId = maleUserId,
					femaleUserId = femaleUserId,
					femaleAccepted = true,
					status = MatchStatus.PARTIALLY_ACCEPTED,
				)
				// 두 사람 프로필 — 성사 알람 문구에 각자 상대의 닉네임이 들어간다.
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = maleUserId, gender = Gender.MALE, nickname = "철수"),
				)
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = femaleUserId, gender = Gender.FEMALE, nickname = "영희"),
				)
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = maleUserId, balance = 100))

				post("/matches/v1/${match.id}/interest") {
					bearer(accessTokenFor(maleUserId))
				} expect {
					status(200)
					body("data.matchStatus", MatchStatus.MATCHED.name)
				}

				// 수락 비용(DATING_ACCEPT=32) 차감 → 잔액 68
				coinBalanceOf(maleUserId) shouldBe 68
				// 성사라 같은 트랜잭션에서 채팅방이 생성된다.
				activeChatRoomCount(maleUserId, femaleUserId) shouldBe 1
				// 상대(여성)에게는 수락자(남성)를 가리키는 '매칭 성사' 알람이 저장된다.
				val femaleAlarms: List<AlarmEntity> = alarmsOf(femaleUserId)
				femaleAlarms.size shouldBe 1
				femaleAlarms[0].type shouldBe AlarmType.MATCHED
				femaleAlarms[0].fromUserId shouldBe maleUserId
				femaleAlarms[0].description shouldBe "철수님과 매칭되었어요!"
				// 수락한 본인(남성)에게도 상대(여성)를 가리키는 '매칭 성사' 알람이 저장된다.
				val maleAlarms: List<AlarmEntity> = alarmsOf(maleUserId)
				maleAlarms.size shouldBe 1
				maleAlarms[0].type shouldBe AlarmType.MATCHED
				maleAlarms[0].fromUserId shouldBe femaleUserId
				maleAlarms[0].description shouldBe "영희님과 매칭되었어요!"
			}
		}

		context("코인 잔액이 부족하면") {
			it("400(COIN-001)을 반환하고 잔액·매칭 상태가 그대로다") {
				val maleUserId = 1003L
				val femaleUserId = 2003L
				val match: MatchEntity = persistMatch(maleUserId, femaleUserId)
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = maleUserId, balance = 10)) // 32보다 적음

				post("/matches/v1/${match.id}/interest") {
					bearer(accessTokenFor(maleUserId))
				} expect {
					status(400)
					body("success", false)
					body("error.code", "COIN-001")
				}

				// 차감 실패로 트랜잭션이 롤백되어 잔액 유지 + 매칭은 PROPOSED 그대로
				coinBalanceOf(maleUserId) shouldBe 10
				matchStatusOf(match.id!!) shouldBe MatchStatus.PROPOSED
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				post("/matches/v1/1/interest") {} expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QMatchMemberEntity.matchMemberEntity)
		IntegrationUtil.deleteAll(QMatchEntity.matchEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
		IntegrationUtil.deleteAll(QCoinHistoryEntity.coinHistoryEntity)
		IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
	}
})

// 해당 사용자의 알람 목록. (알람 저장 확인용)
private fun alarmsOf(userId: Long): List<AlarmEntity> {
	val alarm: QAlarmEntity = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery()
		.selectFrom(alarm)
		.where(alarm.userId.eq(userId))
		.fetch()
}

// 해당 쌍의 ACTIVE 채팅방 개수. (성사 시 채팅방 생성 확인용)
// 참가자는 방이 아니라 참가자(ChatRoomMember) 행이 진실원천이므로, 두 사용자의 참가자 행이 모두 있는 ACTIVE 방을 센다.
private fun activeChatRoomCount(maleUserId: Long, femaleUserId: Long): Long {
	val chatRoom: QChatRoomEntity = QChatRoomEntity.chatRoomEntity
	val maleMember: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
	val femaleMember: QChatRoomMemberEntity = QChatRoomMemberEntity("femaleMember")
	return IntegrationUtil.getQuery()
		.select(chatRoom.count())
		.from(chatRoom)
		.join(maleMember).on(maleMember.chatRoomId.eq(chatRoom.id), maleMember.userId.eq(maleUserId))
		.join(femaleMember).on(femaleMember.chatRoomId.eq(chatRoom.id), femaleMember.userId.eq(femaleUserId))
		.where(chatRoom.status.eq(ChatRoomStatus.ACTIVE))
		.fetchOne()!!
}

// 조회는 리포지토리 대신 IntegrationUtil.getQuery()(QueryDSL)로 수행한다. 스칼라 프로젝션으로 DB 최신값을 읽는다.
private fun coinBalanceOf(userId: Long): Int {
	val coinBalance: QCoinBalanceEntity = QCoinBalanceEntity.coinBalanceEntity
	return IntegrationUtil.getQuery()
		.select(coinBalance.balance)
		.from(coinBalance)
		.where(coinBalance.userId.eq(userId))
		.fetchOne()!!
}

private fun matchStatusOf(matchId: Long): MatchStatus {
	val match: QMatchEntity = QMatchEntity.matchEntity
	return IntegrationUtil.getQuery()
		.select(match.status)
		.from(match)
		.where(match.id.eq(matchId))
		.fetchOne()!!
}
