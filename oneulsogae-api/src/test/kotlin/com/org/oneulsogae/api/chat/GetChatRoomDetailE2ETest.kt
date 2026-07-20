package com.org.oneulsogae.api.chat

import com.org.oneulsogae.common.chat.ChatRoomMatchType
import com.org.oneulsogae.common.chat.ChatRoomMemberStatus
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.chat.command.entity.QChatMessageEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.oneulsogae.infra.fixture.ChatMessageEntityFixture
import com.org.oneulsogae.infra.fixture.ChatRoomEntityFixture
import com.org.oneulsogae.infra.fixture.ChatRoomMemberEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import org.hamcrest.Matchers.hasItems
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import java.time.LocalDateTime

/**
 * `GET /chat/v1/rooms/{chatRoomId}` E2E 테스트.
 *
 * 특정 채팅방의 참여자 정보(참가자들의 프로필)와 채팅 메세지를 함께 조회한다. (조회자는 그 방의 참가자여야 한다)
 * 참가자는 방이 아니라 참가자(ChatRoomMember) 행이 진실원천이므로, 참가자 행을 함께 저장해야 노출·검증된다. (1:1·그룹챗 공통)
 * 실제 서버(RANDOM_PORT) + Testcontainers를 기동하고 HTTP를 호출한다. 준비/정리는 [IntegrationUtil], 요청/검증은 [get]/[expect] DSL로 한다.
 */
class GetChatRoomDetailE2ETest : AbstractIntegrationSupport({

	// 방 한 칸과 참가자 행들을 함께 저장하고 방 id를 반환한다. (참가자가 멤버 단위이므로 참가자 행이 있어야 조회·검증된다)
	fun openRoomWith(matchId: Long, vararg participantUserIds: Long): Long {
		val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = matchId)).id!!
		participantUserIds.forEach { participantUserId: Long ->
			IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = participantUserId))
		}
		return roomId
	}

	describe("GET /chat/v1/rooms/{chatRoomId}") {

		context("참가자가 자신의 채팅방을 조회하면") {
			it("참가자 프로필과 메세지를 최근 순으로 반환한다 (200)") {
				val maleUserId = 7101L
				val femaleUserId = 7102L
				val base: LocalDateTime = LocalDateTime.of(2026, 6, 10, 12, 0)

				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = maleUserId, nickname = "철수", profileImageCode = "IMG_M", gender = Gender.MALE),
				)
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = femaleUserId, nickname = "영희", profileImageCode = "IMG_F", gender = Gender.FEMALE),
				)
				val roomId: Long = openRoomWith(1L, maleUserId, femaleUserId)
				// 오래된 → 최신 순으로 적재(id 증가). 조회는 id 오름차순(과거→최신)으로 내려온다.
				IntegrationUtil.persist(ChatMessageEntityFixture.create(chatRoomId = roomId, senderId = maleUserId, content = "안녕하세요", sentAt = base))
				IntegrationUtil.persist(ChatMessageEntityFixture.create(chatRoomId = roomId, senderId = femaleUserId, content = "반가워요", sentAt = base.plusMinutes(1)))
				val lastMessageId: Long = IntegrationUtil.persist(
					ChatMessageEntityFixture.create(chatRoomId = roomId, senderId = maleUserId, content = "오늘 뭐 해요?", sentAt = base.plusMinutes(2)),
				).id!!

				get("/chat/v1/rooms/$roomId") {
					bearer(accessTokenFor(maleUserId))
				} expect {
					status(200)
					body("success", true)
					body("data.chatRoomId", roomId.toInt())
					body("data.type", ChatRoomMatchType.SOLO.name)
					// 참여자 2명 (순서 무관), 프로필 동반
					body("data.participants.size()", 2)
					body("data.participants.userId", hasItems(maleUserId.toInt(), femaleUserId.toInt()))
					body("data.participants.nickname", hasItems("철수", "영희"))
					// SOLO 방은 팀이 없어 조회자 자신만 isMyTeam=true (참가자는 userId 오름차순: index0=나, index1=상대)
					body("data.participants[0].isMyTeam", true)
					body("data.participants[1].isMyTeam", false)
					// 메세지 3건, 과거부터(id 오름차순)
					body("data.messages.size()", 3)
					body("data.messages[0].senderId", maleUserId.toInt())
					body("data.messages[0].content", "안녕하세요")
					body("data.messages[2].messageId", lastMessageId.toInt())
					body("data.messages[2].content", "오늘 뭐 해요?")
					// 더 과거를 이어 읽을 커서(가장 오래된 메세지 id)가 내려온다
					body("data.nextCursor", notNullValue())
				}
			}
		}

		context("참가자가 3명인 그룹 채팅방을 조회하면") {
			it("세 참가자 프로필을 모두 반환한다 (200)") {
				val me = 7301L
				val partnerA = 7302L
				val partnerB = 7303L

				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = me, nickname = "나"))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = partnerA, nickname = "상대A"))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = partnerB, nickname = "상대B"))
				val roomId: Long = openRoomWith(30L, me, partnerA, partnerB)

				get("/chat/v1/rooms/$roomId") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.participants.size()", 3)
					body("data.participants.userId", hasItems(me.toInt(), partnerA.toInt(), partnerB.toInt()))
					body("data.participants.nickname", hasItems("나", "상대A", "상대B"))
				}
			}
		}

		context("참가자 한 명이 나간 그룹 채팅방을 조회하면") {
			it("나간 참가자도 프로필과 함께 그대로 노출한다 (200)") {
				val me = 7311L
				val stayed = 7312L
				val left = 7313L

				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = me, nickname = "나"))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = stayed, nickname = "남은사람"))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = left, nickname = "나간사람"))

				val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = 31L)).id!!
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = me))
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = stayed))
				IntegrationUtil.persist(
					ChatRoomMemberEntityFixture.create(
						chatRoomId = roomId,
						userId = left,
						exitedAt = LocalDateTime.of(2026, 6, 10, 12, 0),
					),
				)

				get("/chat/v1/rooms/$roomId") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					// 나간 참가자 포함 3명 모두 노출
					body("data.participants.size()", 3)
					body("data.participants.userId", hasItems(me.toInt(), stayed.toInt(), left.toInt()))
					body("data.participants.nickname", hasItems("나", "남은사람", "나간사람"))
				}
			}
		}

		context("커서로 이후 페이지를 조회하면") {
			it("방·참여자 데이터 없이 더 과거 메세지만 반환한다 (200)") {
				val me = 7401L
				val partner = 7402L
				val base: LocalDateTime = LocalDateTime.of(2026, 6, 10, 12, 0)

				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = me, nickname = "나"))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = partner, nickname = "상대"))
				val roomId: Long = openRoomWith(40L, me, partner)
				val m1: Long = IntegrationUtil.persist(ChatMessageEntityFixture.create(chatRoomId = roomId, senderId = me, content = "1", sentAt = base)).id!!
				val m2: Long = IntegrationUtil.persist(ChatMessageEntityFixture.create(chatRoomId = roomId, senderId = partner, content = "2", sentAt = base.plusMinutes(1))).id!!
				val m3: Long = IntegrationUtil.persist(ChatMessageEntityFixture.create(chatRoomId = roomId, senderId = me, content = "3", sentAt = base.plusMinutes(2))).id!!

				// m3보다 과거(cursor = m3) 요청 → m1, m2만 (과거부터, id 오름차순)
				get("/chat/v1/rooms/$roomId?cursor=$m3&size=10") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.chatRoomId", roomId.toInt())
					// 방·참여자 데이터는 첫 페이지에서만 → 이후 페이지엔 없음
					body("data.type", nullValue())
					body("data.status", nullValue())
					body("data.participants.size()", 0)
					// 더 과거 메세지만, 과거부터(id 오름차순)
					body("data.messages.size()", 2)
					body("data.messages[0].messageId", m1.toInt())
					body("data.messages[1].messageId", m2.toInt())
				}
			}

			it("참가자가 아니면 커서 조회도 403(CHAT-002)을 반환한다 (접근 검증은 매 요청)") {
				val me = 7411L
				val partner = 7412L
				val strangerId = 7499L
				val roomId: Long = openRoomWith(41L, me, partner)

				get("/chat/v1/rooms/$roomId?cursor=999999") {
					bearer(accessTokenFor(strangerId))
				} expect {
					status(403)
					body("success", false)
					body("error.code", "CHAT-002")
				}
			}
		}

		context("참가자가 아닌 사용자가 조회하면") {
			it("403(CHAT-002)을 반환한다") {
				val maleUserId = 7111L
				val femaleUserId = 7112L
				val strangerId = 7199L
				val roomId: Long = openRoomWith(11L, maleUserId, femaleUserId)

				get("/chat/v1/rooms/$roomId") {
					bearer(accessTokenFor(strangerId))
				} expect {
					status(403)
					body("success", false)
					body("error.code", "CHAT-002")
				}
			}
		}

		context("존재하지 않는 채팅방을 조회하면") {
			it("404(CHAT-001)을 반환한다") {
				get("/chat/v1/rooms/999999") {
					bearer(accessTokenFor(7121L))
				} expect {
					status(404)
					body("success", false)
					body("error.code", "CHAT-001")
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				get("/chat/v1/rooms/1") {} expect {
					status(401)
				}
			}
		}

		context("참가자별 읽음 상태를 함께 조회하면") {
			it("각 참가자의 읽음 포인터와 활성여부를 반환한다 (200)") {
				val me = 7501L          // me < left → participants는 userId 오름차순 정렬
				val left = 7502L
				val base: LocalDateTime = LocalDateTime.of(2026, 6, 10, 12, 0)

				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = me, nickname = "나"))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = left, nickname = "나간사람"))

				val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = 50L)).id!!
				IntegrationUtil.persist(
					ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = me, lastReadMessageId = 55L),
				)
				IntegrationUtil.persist(
					ChatRoomMemberEntityFixture.create(
						chatRoomId = roomId,
						userId = left,
						status = ChatRoomMemberStatus.DEACTIVE,
						lastReadMessageId = 40L,
						exitedAt = base,
					),
				)

				get("/chat/v1/rooms/$roomId") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.participants.size()", 2)
					// 활성 참가자(나): 포인터 55, active true
					body("data.participants[0].userId", me.toInt())
					body("data.participants[0].lastReadMessageId", 55)
					body("data.participants[0].active", true)
					// 나간 참가자: 포인터 40(그대로 노출), active false
					body("data.participants[1].userId", left.toInt())
					body("data.participants[1].lastReadMessageId", 40)
					body("data.participants[1].active", false)
				}
			}
		}

		context("TEAM(2:2) 방을 조회하면") {
			it("조회자 자신·내 팀원은 isMyTeam=true, 상대 팀은 false로 내려준다 (200)") {
				val me = 7601L          // myTeam
				val teammate = 7602L    // myTeam
				val opponentA = 7603L   // oppTeam
				val opponentB = 7604L   // oppTeam (participants는 userId 오름차순)
				val myTeamId = 100L
				val oppTeamId = 200L

				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = me, nickname = "나"))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = teammate, nickname = "내팀원"))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = opponentA, nickname = "상대A"))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = opponentB, nickname = "상대B"))

				val roomId: Long = IntegrationUtil.persist(
					ChatRoomEntityFixture.create(matchType = ChatRoomMatchType.TEAM, matchId = 60L),
				).id!!
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = me, teamId = myTeamId))
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = teammate, teamId = myTeamId))
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = opponentA, teamId = oppTeamId))
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = opponentB, teamId = oppTeamId))

				get("/chat/v1/rooms/$roomId") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.type", ChatRoomMatchType.TEAM.name)
					// 상세는 내 팀원·상대 팀 전원 노출 (목록과 달리 팀 필터 없음)
					body("data.participants.size()", 4)
					// userId 오름차순: [나, 내팀원, 상대A, 상대B]
					body("data.participants[0].isMyTeam", true)  // 나
					body("data.participants[1].isMyTeam", true)  // 내 팀원
					body("data.participants[2].isMyTeam", false) // 상대 팀
					body("data.participants[3].isMyTeam", false) // 상대 팀
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QChatMessageEntity.chatMessageEntity)
		IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
	}
})
