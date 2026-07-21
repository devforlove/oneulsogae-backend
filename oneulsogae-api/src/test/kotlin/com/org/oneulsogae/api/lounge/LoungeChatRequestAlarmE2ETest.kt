package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.alarm.command.entity.AlarmEntity
import com.org.oneulsogae.infra.alarm.command.entity.QAlarmEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.fixture.CoinBalanceEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.LoungePostEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.lounge.command.entity.LoungePostEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungeChatRequestEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungePostEntity
import io.kotest.matchers.shouldBe
import io.restassured.RestAssured
import io.restassured.path.json.JsonPath

/**
 * 라운지 대화 신청·수락 알람 E2E 테스트.
 * 신청하면 작성자에게 "대화 신청 받음", 수락하면 신청자에게 "대화 신청 수락됨" 알람이 쌓이는지 검증한다.
 * 알람은 AFTER_COMMIT 리스너가 같은 요청 스레드에서 동기로 저장하므로(응답 전 저장 완료),
 * 기존 매칭 알람 E2E([com.org.oneulsogae.api.match.SendInterestE2ETest])와 같이 대기 없이 바로 단언한다.
 */
class LoungeChatRequestAlarmE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
		IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		IntegrationUtil.deleteAll(QLoungeChatRequestEntity.loungeChatRequestEntity)
		IntegrationUtil.deleteAll(QLoungePostEntity.loungePostEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
	}

	describe("라운지 대화 신청·수락 알람") {

		context("신청하고 작성자가 수락하면") {
			it("작성자에게 신청 알람이, 신청자에게 수락 알람이 쌓인다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-alarm-author")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-alarm-user")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = authorId, nickname = "글쓴이", gender = Gender.FEMALE))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = requesterId, nickname = "신청자", gender = Gender.MALE, companyName = "오늘소개"))
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = authorId, balance = 100))
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = requesterId, balance = 100))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				val requestBody: String = RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(requesterId)}")
					.post("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(200)
					.extract()
					.asString()
				val requestId: Int = JsonPath(requestBody).getInt("data.requestId")

				// 글 작성자에게 "대화 신청 받음" 알람.
				val requestAlarms: List<AlarmEntity> = alarmsOf(authorId)
				requestAlarms.size shouldBe 1
				val requestAlarm: AlarmEntity = requestAlarms[0]
				requestAlarm.type shouldBe AlarmType.LOUNGE_CHAT_REQUEST_RECEIVED
				requestAlarm.fromUserId shouldBe requesterId
				requestAlarm.description shouldBe "신청자님이 회원님에게 대화를 신청했어요."
				requestAlarm.link shouldBe "/"

				val acceptBody: String = RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.post("/lounge/v1/chat-requests/$requestId/accept")
					.then()
					.statusCode(200)
					.extract()
					.asString()
				val chatRoomId: Int = JsonPath(acceptBody).getInt("data.chatRoomId")

				// 신청자에게 "대화 신청 수락됨" 알람. 누르면 생성된 채팅방으로 이동한다.
				val acceptAlarms: List<AlarmEntity> = alarmsOf(requesterId)
				acceptAlarms.size shouldBe 1
				val acceptAlarm: AlarmEntity = acceptAlarms[0]
				acceptAlarm.type shouldBe AlarmType.LOUNGE_CHAT_ACCEPTED
				acceptAlarm.fromUserId shouldBe authorId
				acceptAlarm.description shouldBe "글쓴이님이 대화 신청을 수락했어요."
				acceptAlarm.link shouldBe "/chat/$chatRoomId"
			}
		}
	}
})

// 해당 사용자의 알람 목록. (알람 저장 확인용 — SendInterestE2ETest와 같은 형태)
private fun alarmsOf(userId: Long): List<AlarmEntity> {
	val alarm: QAlarmEntity = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery()
		.selectFrom(alarm)
		.where(alarm.userId.eq(userId))
		.fetch()
}
