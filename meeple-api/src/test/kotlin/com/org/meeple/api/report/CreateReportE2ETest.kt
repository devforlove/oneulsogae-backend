package com.org.meeple.api.report

import com.org.meeple.common.chat.ChatRoomMatchType
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.report.ReportType
import com.org.meeple.infra.chat.command.entity.QChatRoomEntity
import com.org.meeple.infra.fixture.ChatRoomEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.report.command.entity.QReportEntity
import com.org.meeple.infra.report.command.entity.ReportEntity
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * `POST /reports/v1` E2E 테스트. (신고 생성 엔드포인트)
 * 신고 대상(상대 유저/팀)을 요청으로 받지 않고, chatRoomId로 채팅방의 매칭 정보(matchType+matchId)를 얻어 매칭 종류에 따라 채운다.
 * - SOLO 채팅방: 그 방의 matchId를 to_user_id로 저장한다.
 * - TEAM 채팅방: 그 방의 matchId를 to_team_id로 저장한다.
 */
class CreateReportE2ETest : AbstractIntegrationSupport({

	// 매칭 타입+id를 가진 ACTIVE 채팅방을 만들고 채팅방 id를 반환한다.
	fun persistChatRoom(matchType: ChatRoomMatchType, matchId: Long): Long =
		IntegrationUtil.persist(ChatRoomEntityFixture.create(matchType = matchType, matchId = matchId)).id!!

	fun reportOf(fromUserId: Long): ReportEntity? {
		val report: QReportEntity = QReportEntity.reportEntity
		return IntegrationUtil.getQuery().selectFrom(report).where(report.fromUserId.eq(fromUserId)).fetchFirst()
	}

	describe("POST /reports/v1") {

		context("SOLO 채팅방에서 신고하면") {
			it("그 방의 matchId를 to_user_id로 채워 저장한다 (200)") {
				val reporter = 8001L
				val partnerUserId = 12345L
				val chatRoomId: Long = persistChatRoom(ChatRoomMatchType.SOLO, partnerUserId)

				post("/reports/v1") {
					bearer(accessTokenFor(reporter))
					jsonBody("""{"type": "ABUSE_DEFAMATION", "chatRoomId": $chatRoomId, "description": "욕설을 했어요"}""")
				} expect {
					status(200)
					body("success", true)
				}

				val saved: ReportEntity = reportOf(reporter)!!
				saved.type shouldBe ReportType.ABUSE_DEFAMATION
				saved.toUserId shouldBe partnerUserId
				saved.toTeamId.shouldBeNull()
				saved.chatRoomId shouldBe chatRoomId
				saved.description shouldBe "욕설을 했어요"
			}
		}

		context("TEAM 채팅방에서 신고하면") {
			it("그 방의 matchId를 to_team_id로 채워 저장한다 (200)") {
				val reporter = 8101L
				val opponentTeamId = 67890L
				val chatRoomId: Long = persistChatRoom(ChatRoomMatchType.TEAM, opponentTeamId)

				post("/reports/v1") {
					bearer(accessTokenFor(reporter))
					jsonBody("""{"type": "FRAUD_IMPERSONATION", "chatRoomId": $chatRoomId}""")
				} expect {
					status(200)
					body("success", true)
				}

				val saved: ReportEntity = reportOf(reporter)!!
				saved.type shouldBe ReportType.FRAUD_IMPERSONATION
				saved.toTeamId shouldBe opponentTeamId
				saved.toUserId.shouldBeNull()
			}
		}

		context("존재하지 않는 채팅방을 신고하면") {
			it("404(CHAT-001)를 반환하고 신고를 저장하지 않는다") {
				val reporter = 8003L

				post("/reports/v1") {
					bearer(accessTokenFor(reporter))
					jsonBody("""{"type": "SPAM_ADVERTISEMENT", "chatRoomId": 999999}""")
				} expect {
					status(404)
					body("success", false)
					body("error.code", "CHAT-001")
				}

				reportOf(reporter).shouldBeNull()
			}
		}

		context("type·chatRoomId가 없으면") {
			it("400을 반환한다") {
				post("/reports/v1") {
					bearer(accessTokenFor(8005L))
					jsonBody("""{"description": "내용만 있음"}""")
				} expect {
					status(400)
					body("success", false)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QReportEntity.reportEntity)
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
	}
})
