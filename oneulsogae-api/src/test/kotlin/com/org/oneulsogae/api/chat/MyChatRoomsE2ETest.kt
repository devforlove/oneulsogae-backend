package com.org.oneulsogae.api.chat

import com.org.oneulsogae.common.chat.ChatRoomMatchType
import com.org.oneulsogae.common.chat.ChatRoomMemberStatus
import com.org.oneulsogae.common.chat.ChatRoomStatus
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.chat.command.entity.ChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.oneulsogae.infra.fixture.ChatRoomEntityFixture
import com.org.oneulsogae.infra.fixture.ChatRoomMemberEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import org.hamcrest.Matchers.hasItems
import java.time.LocalDateTime

/**
 * `GET /chat/v1/rooms` E2E 테스트.
 *
 * 현재 로그인 사용자가 참가한 ACTIVE 상태의 채팅방 목록을 조회한다.
 * 참가/읽음 상태가 참가자(ChatRoomMember) 단위이므로, 서버는 사용자의 참가자 행(user_id)에서 출발해 방을 찾고,
 * 그 방들의 상대 참가자들(나 제외)을 프로필과 함께 묶어, 사용자 관점의 안 읽은 개수와 같이 내려준다. (방 정체성은 참가자에서 파생, 1:1·그룹챗 공통)
 * 실제 서버(RANDOM_PORT) + Testcontainers를 기동하고 HTTP를 호출한다.
 * 데이터 준비/정리는 [IntegrationUtil], 요청/검증은 [get]/[expect] Kotlin DSL로 한다.
 */
class MyChatRoomsE2ETest : AbstractIntegrationSupport({

	// 방 한 칸과 참가자 행들(읽음 개수 포함)을 함께 저장한다. (참가/읽음 상태가 멤버 단위이므로 참가자 행이 있어야 조회된다)
	// members: (userId, unreadCount) 쌍 목록. 1:1이면 두 명, 그룹챗이면 세 명 이상을 넘긴다.
	fun openRoomWithMembers(
		matchId: Long,
		members: List<Pair<Long, Int>>,
		status: ChatRoomStatus = ChatRoomStatus.ACTIVE,
		lastMessage: String? = null,
		lastMessageAt: LocalDateTime? = null,
	): Long {
		val room: ChatRoomEntity = IntegrationUtil.persist(
			ChatRoomEntityFixture.create(
				matchId = matchId,
				status = status,
				lastMessage = lastMessage,
				lastMessageAt = lastMessageAt,
			),
		)
		val roomId: Long = room.id!!
		members.forEach { (memberUserId: Long, unreadCount: Int) ->
			IntegrationUtil.persist(
				ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = memberUserId, unreadCount = unreadCount),
			)
		}
		return roomId
	}

	// TEAM(2:2) 방과 참가자 행들을 함께 저장한다. members: (userId, unreadCount, teamId) 목록.
	fun openTeamRoomWithMembers(
		matchId: Long,
		members: List<Triple<Long, Int, Long?>>,
	): Long {
		val room: ChatRoomEntity = IntegrationUtil.persist(
			ChatRoomEntityFixture.create(matchType = ChatRoomMatchType.TEAM, matchId = matchId),
		)
		val roomId: Long = room.id!!
		members.forEach { (memberUserId: Long, unreadCount: Int, teamId: Long?) ->
			IntegrationUtil.persist(
				ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = memberUserId, unreadCount = unreadCount, teamId = teamId),
			)
		}
		return roomId
	}

	describe("GET /chat/v1/rooms") {

		context("사용자에게 ACTIVE/EXPIRED 채팅방과 타인의 방이 섞여 있으면") {
			it("본인이 참가한 ACTIVE 방만 최근 대화 순으로, 상대 프로필과 함께 사용자 관점으로 반환한다") {
				val userId = 7001L
				val partnerA = 8001L
				val partnerB = 8002L
				val base: LocalDateTime = LocalDateTime.of(2026, 6, 10, 12, 0)

				// 상대방 프로필 (조인 대상) — 상대 프로필이 있어야 목록에 노출된다
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = partnerA, nickname = "지민", profileImageCode = "IMG_A", gender = Gender.FEMALE),
				)
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = partnerB, nickname = "수아", profileImageCode = "IMG_B", gender = Gender.FEMALE),
				)

				// 본인이 참가한 ACTIVE 방 (마지막 메세지 더 최근) — 본인 안 읽음 2
				openRoomWithMembers(
					matchId = 1L,
					members = listOf(userId to 2, partnerA to 9),
					lastMessage = "최근 메세지",
					lastMessageAt = base.plusHours(2),
				)
				// 본인이 참가한 ACTIVE 방 (마지막 메세지 더 과거) — 본인 안 읽음 1
				openRoomWithMembers(
					matchId = 2L,
					members = listOf(userId to 1, partnerB to 9),
					lastMessage = "예전 메세지",
					lastMessageAt = base.plusHours(1),
				)
				// 본인의 EXPIRED 방 — 제외돼야 함 (본인 참가자 행은 있으나 status로 걸러진다)
				openRoomWithMembers(
					matchId = 3L,
					members = listOf(userId to 0, 8003L to 0),
					status = ChatRoomStatus.EXPIRED,
				)
				// 타인끼리의 ACTIVE 방 — 본인 참가자 행이 없어 제외된다
				openRoomWithMembers(matchId = 4L, members = listOf(9001L to 0, 9002L to 0))

				get("/chat/v1/rooms") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					// 본인의 ACTIVE 방 2건만 (방당 한 건씩, 중복행 없음)
					body("data.size()", 2)
					// 마지막 메세지 최근 순: partnerA 방이 먼저. 상대 참가자 1명이 프로필과 함께 내려온다
					body("data[0].participants.size()", 1)
					body("data[0].participants[0].userId", partnerA.toInt())
					body("data[0].participants[0].nickname", "지민")
					body("data[0].participants[0].profileImageCode", "IMG_A")
					body("data[0].participants[0].gender", Gender.FEMALE.name)
					body("data[0].type", ChatRoomMatchType.SOLO.name)
					body("data[0].status", ChatRoomStatus.ACTIVE.name)
					body("data[0].unreadCount", 2) // 본인 관점
					body("data[0].lastMessage", "최근 메세지")
					body("data[1].participants[0].userId", partnerB.toInt())
					body("data[1].participants[0].nickname", "수아")
					body("data[1].unreadCount", 1)
				}
			}
		}

		context("참가자가 3명인 그룹 채팅방을 조회하면") {
			it("방은 한 건으로(중복행 없이), 나를 제외한 상대 두 명을 participants로 내려준다") {
				val userId = 7004L
				val partnerA = 8101L
				val partnerB = 8102L

				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = partnerA, nickname = "상대A"))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = partnerB, nickname = "상대B"))

				openRoomWithMembers(
					matchId = 10L,
					members = listOf(userId to 4, partnerA to 0, partnerB to 0),
					lastMessage = "그룹 메세지",
				)

				get("/chat/v1/rooms") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					// 참가자 3명이어도 방은 한 건 (1대다 조인 중복행 방지)
					body("data.size()", 1)
					// 나를 제외한 상대 두 명
					body("data[0].participants.size()", 2)
					body("data[0].participants.userId", hasItems(partnerA.toInt(), partnerB.toInt()))
					body("data[0].participants.nickname", hasItems("상대A", "상대B"))
					body("data[0].unreadCount", 4) // 본인 관점
				}
			}
		}

		context("그룹 채팅방에서 일부 참가자가 나갔어도") {
			it("나간 참가자도 participants에 그대로 노출한다 (조회자 본인만 제외)") {
				val userId = 7005L
				val stayed = 8201L
				val left = 8202L

				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = stayed, nickname = "남은사람"))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = left, nickname = "나간사람"))

				val roomId: Long = openRoomWithMembers(
					matchId = 12L,
					members = listOf(userId to 0, stayed to 0),
					lastMessage = "그룹 메세지",
				)
				// 나간(exited) 참가자를 한 명 더 추가한다
				IntegrationUtil.persist(
					ChatRoomMemberEntityFixture.create(
						chatRoomId = roomId,
						userId = left,
						exitedAt = LocalDateTime.of(2026, 6, 10, 12, 0),
					),
				)

				get("/chat/v1/rooms") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.size()", 1)
					// 남은 사람 + 나간 사람 모두 노출 (나만 제외)
					body("data[0].participants.size()", 2)
					body("data[0].participants.userId", hasItems(stayed.toInt(), left.toInt()))
					body("data[0].participants.nickname", hasItems("남은사람", "나간사람"))
				}
			}
		}

		context("상대가 채팅방을 나가(비활성) 본인만 남아도") {
			it("나간(DEACTIVE) 상대의 프로필을 participants에 그대로 노출한다") {
				val userId = 7006L
				val left = 8301L

				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = left, nickname = "떠난사람", profileImageCode = "IMG_LEFT", gender = Gender.FEMALE),
				)

				val roomId: Long = openRoomWithMembers(
					matchId = 13L,
					members = listOf(userId to 0),
					lastMessage = "마지막 인사",
				)
				// 상대는 나가기로 비활성(DEACTIVE)된 상태 — 그래도 프로필은 노출돼야 한다
				IntegrationUtil.persist(
					ChatRoomMemberEntityFixture.create(
						chatRoomId = roomId,
						userId = left,
						status = ChatRoomMemberStatus.DEACTIVE,
					),
				)

				get("/chat/v1/rooms") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.size()", 1)
					// 비활성(DEACTIVE) 상대도 프로필과 함께 노출
					body("data[0].participants.size()", 1)
					body("data[0].participants[0].userId", left.toInt())
					body("data[0].participants[0].nickname", "떠난사람")
					body("data[0].participants[0].profileImageCode", "IMG_LEFT")
					body("data[0].participants[0].gender", Gender.FEMALE.name)
				}
			}
		}

		context("본인이 참가한 1:1 방을 조회하면") {
			it("상대 프로필과 본인 관점의 안 읽은 개수로 반환한다") {
				val userId = 7003L
				val partner = 8004L

				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = partner, nickname = "민준", profileImageCode = "IMG_M", gender = Gender.MALE),
				)
				// 본인 안 읽음 3
				openRoomWithMembers(
					matchId = 5L,
					members = listOf(partner to 9, userId to 3),
					lastMessage = "안녕하세요",
				)

				get("/chat/v1/rooms") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.size()", 1)
					body("data[0].participants.size()", 1)
					body("data[0].participants[0].userId", partner.toInt())
					body("data[0].participants[0].nickname", "민준")
					body("data[0].participants[0].profileImageCode", "IMG_M")
					body("data[0].participants[0].gender", Gender.MALE.name)
					body("data[0].unreadCount", 3) // 본인 관점
				}
			}
		}

		context("참가한 ACTIVE 채팅방이 없으면") {
			it("빈 목록을 반환한다") {
				val userId = 7002L

				get("/chat/v1/rooms") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data.size()", 0)
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				get("/chat/v1/rooms") {} expect {
					status(401)
				}
			}
		}

		context("TEAM(2:2) 매칭 채팅방을 조회하면") {
			it("내 팀원을 제외하고 상대 팀 구성원만 participants로 내려준다") {
				val userId = 7201L
				val teammate = 7202L
				val opponentA = 7203L
				val opponentB = 7204L
				val myTeamId = 100L
				val oppTeamId = 200L

				// 팀원도 프로필을 둬서, 팀원이 '프로필 없음'이 아니라 팀 필터로 빠지는 것을 검증한다
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = teammate, nickname = "내팀원", gender = Gender.MALE))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = opponentA, nickname = "상대A", gender = Gender.FEMALE))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = opponentB, nickname = "상대B", gender = Gender.FEMALE))

				openTeamRoomWithMembers(
					matchId = 50L,
					members = listOf(
						Triple(userId, 3, myTeamId),
						Triple(teammate, 0, myTeamId),
						Triple(opponentA, 0, oppTeamId),
						Triple(opponentB, 0, oppTeamId),
					),
				)

				get("/chat/v1/rooms") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data.size()", 1)
					body("data[0].type", ChatRoomMatchType.TEAM.name)
					// 상대 팀 2명만 (내 팀원 제외)
					body("data[0].participants.size()", 2)
					body("data[0].participants.userId", hasItems(opponentA.toInt(), opponentB.toInt()))
					body("data[0].unreadCount", 3) // 본인 관점
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
	}
})
