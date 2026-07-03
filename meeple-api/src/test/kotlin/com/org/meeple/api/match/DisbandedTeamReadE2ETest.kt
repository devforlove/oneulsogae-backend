package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.delete
import com.org.meeple.common.integration.expect
import com.org.meeple.common.chat.ChatRoomMatchType
import com.org.meeple.common.integration.get
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchedTeamStatus
import com.org.meeple.common.match.TeamMatchType
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.alarm.command.entity.QAlarmEntity
import com.org.meeple.infra.chat.command.entity.QChatMessageEntity
import com.org.meeple.infra.chat.command.entity.QChatRoomEntity
import com.org.meeple.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.meeple.infra.fixture.ChatRoomEntityFixture
import com.org.meeple.infra.fixture.ChatRoomMemberEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.teammatch.command.entity.MatchedTeamEntity
import com.org.meeple.infra.matchuser.command.entity.QMatchUserEntity
import com.org.meeple.infra.teammatch.command.entity.QMatchedTeamEntity
import com.org.meeple.infra.teammatch.command.entity.QTeamEntity
import com.org.meeple.infra.teammatch.command.entity.QTeamMatchEntity
import com.org.meeple.infra.teammatch.command.entity.QTeamMemberEntity
import com.org.meeple.infra.teammatch.command.entity.TeamEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.teammatch.command.entity.TeamMatchEntity
import com.org.meeple.infra.teammatch.command.entity.TeamMemberEntity
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
import java.time.LocalDate
import java.time.LocalDateTime
import io.kotest.core.annotation.Ignored

/**
 * 팀 해체(=구성원 탈퇴) 이후의 조회 동작 E2E.
 *
 * 팀은 구성원 단위로 떠난다(`DELETE /teams/v1/{teamId}`). 떠난 본인은 더는 ACTIVE 구성원이 아니다.
 * 해체 뒤 조회 경로가 의도대로 바뀌는지 검증한다.
 * - 미팅탭: 떠난 본인은 ACTIVE 구성원이 아니라 `myTeam=null`, 추천 슬롯이 "매칭된 상대 팀"에서 추천 팀 경로(시드 없으면 빈 리스트)로 되돌아간다.
 * - 채팅방 목록: 성사(MATCHED) 매칭이던 방에서 떠난 본인의 참가만 비활성화돼 본인 목록에서 사라지고, 남은 팀원·상대 팀에는 그대로 남는다.
 */
// [미팅 기능 미노출] 팀 매칭 컨트롤러(@RestController)가 주석 처리되어 엔드포인트가 404를 반환하므로 이 스펙을 비활성화한다.
// 기능 노출 시 컨트롤러 복구와 함께 @Ignored를 제거한다.
@Ignored
class DisbandedTeamReadE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender = Gender.MALE) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	fun persistTeam(gender: Gender = Gender.MALE): Long =
		IntegrationUtil.persist(
			TeamEntity(name = "팀", gender = gender, regionId = 1L, introduction = "함께 즐겁게 활동해요", status = TeamStatus.ACTIVE),
		).id!!

	fun persistMember(teamId: Long, userId: Long) {
		IntegrationUtil.persist(TeamMemberEntity(teamId = teamId, userId = userId, status = TeamMemberStatus.ACTIVE))
	}

	// 두 팀을 성사(MATCHED) 상태로 묶은 팀 매칭을 만들고 teamMatchId를 돌려준다. (만료 먼 미래 → 진행 중)
	fun persistMatchedMatch(myTeamId: Long, opponentTeamId: Long): Long {
		val header: TeamMatchEntity = IntegrationUtil.persist(
			TeamMatchEntity(
				memberKey = listOf(myTeamId, opponentTeamId).sorted().joinToString("-"),
				introducedDate = LocalDate.of(2026, 6, 24),
				expiresAt = LocalDateTime.of(2999, 1, 1, 0, 0),
				status = MatchStatus.MATCHED,
				matchType = TeamMatchType.RECOMMENDED,
				dateInitAmount = 40,
				dateAcceptAmount = 40,
			),
		)
		val teamMatchId: Long = header.id!!
		IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = teamMatchId, teamId = myTeamId, status = MatchedTeamStatus.ACTIVE))
		IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = teamMatchId, teamId = opponentTeamId, status = MatchedTeamStatus.ACTIVE))
		return teamMatchId
	}

	describe("DELETE /teams/v1/{teamId} — 해체 후 조회") {

		context("매칭 진행 중이던 결성 팀을 해체하면, 해체한 구성원의 미팅탭은") {
			it("myTeam=null로 바뀌고 매칭됐던 상대 팀이 추천 슬롯에서 사라진다") {
				val me = 6001L
				val friend = 6002L
				persistMatchUser(me)
				persistMatchUser(friend)
				val myTeamId: Long = persistTeam(Gender.MALE)
				persistMember(myTeamId, me)
				persistMember(myTeamId, friend)

				val opponentTeamId: Long = persistTeam(Gender.FEMALE)
				persistMatchedMatch(myTeamId, opponentTeamId)

				// 해체 전: 내 결성 팀과 매칭된 상대 팀이 추천 슬롯에 내려온다.
				get("/team-matches/v1/meeting-tab") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.myTeam.teamId", myTeamId.toInt())
					body("data.recommendedTeams", hasSize<Any>(1))
					body("data.recommendedTeams[0].teamId", opponentTeamId.toInt())
					body("data.receivedInvitationCount", 0)
				}

				delete("/teams/v1/$myTeamId") { bearer(accessTokenFor(me)) } expect {
					status(200)
					body("success", true)
				}

				// 해체 후: 결성 팀이 사라져 myTeam=null, 추천 슬롯은 추천 팀 경로(시드 없음 → 빈 리스트)로 되돌아간다.
				get("/team-matches/v1/meeting-tab") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.myTeam", nullValue())
					body("data.recommendedTeams", hasSize<Any>(0))
					body("data.receivedInvitationCount", 0)
				}
			}
		}

		context("성사(MATCHED) 매칭의 채팅방이 있는 팀에서 구성원이 떠나면, 채팅방 목록은") {
			it("떠난 본인에게만 방이 사라지고, 남은 팀원·상대 팀에는 그대로 남는다 (마지막 구성원까지 떠나면 본인도 사라짐)") {
				val me = 6101L
				val friend = 6102L
				val opponentOwner = 6103L
				val opponentFriend = 6104L
				val myTeamId: Long = persistTeam(Gender.MALE)
				persistMember(myTeamId, me)
				persistMember(myTeamId, friend)
				val opponentTeamId: Long = persistTeam(Gender.FEMALE)
				persistMember(opponentTeamId, opponentOwner)
				persistMember(opponentTeamId, opponentFriend)

				val teamMatchId: Long = persistMatchedMatch(myTeamId, opponentTeamId)

				// 성사 매칭의 채팅방 + 양 팀 참가자 (matchId == teamMatchId 전제)
				val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchType = ChatRoomMatchType.TEAM, matchId = teamMatchId)).id!!
				listOf(me, friend, opponentOwner, opponentFriend).forEach { uid: Long ->
					IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = uid))
				}

				// 해체 전: 나는 매칭된 채팅방을 조회할 수 있다.
				get("/chat/v1/rooms") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.size()", 1)
				}

				// 1단계: me가 떠난다.
				delete("/teams/v1/$myTeamId") { bearer(accessTokenFor(me)) } expect {
					status(200)
					body("success", true)
				}

				// 떠난 본인(me) 목록에서만 방이 사라진다.
				get("/chat/v1/rooms") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data.size()", 0)
				}
				// 남은 팀원(friend)과 상대 팀 목록에는 방이 그대로 남는다.
				get("/chat/v1/rooms") {
					bearer(accessTokenFor(friend))
				} expect {
					status(200)
					body("data.size()", 1)
				}
				get("/chat/v1/rooms") {
					bearer(accessTokenFor(opponentOwner))
				} expect {
					status(200)
					body("data.size()", 1)
				}

				// 2단계: 남은 friend가 마저 떠나면 friend 목록에서도 방이 사라진다. (상대 팀은 여전히 유지)
				delete("/teams/v1/$myTeamId") { bearer(accessTokenFor(friend)) } expect {
					status(200)
					body("success", true)
				}
				get("/chat/v1/rooms") {
					bearer(accessTokenFor(friend))
				} expect {
					status(200)
					body("data.size()", 0)
				}
				get("/chat/v1/rooms") {
					bearer(accessTokenFor(opponentOwner))
				} expect {
					status(200)
					body("data.size()", 1)
				}
			}
		}

		context("1단계로 DISBANDED가 된(매칭 유지) 팀은 미팅탭 조회에 그대로 노출된다") {
			it("남은 본인은 내 팀(DISBANDED·partner null)과 매칭된 상대 카드를 보고, 상대 팀도 내 DISBANDED 팀 카드를 본다") {
				val me = 6201L
				val friend = 6202L
				val opponentOwner = 6203L
				val opponentFriend = 6204L
				persistMatchUser(me)
				persistMatchUser(friend)
				persistMatchUser(opponentOwner, Gender.FEMALE)
				persistMatchUser(opponentFriend, Gender.FEMALE)
				// 카드 구성원 로딩(match_user ⋈ user_details inner join)을 위해 표시될 구성원의 상세를 넣는다.
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = friend))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = opponentOwner, gender = Gender.FEMALE))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = opponentFriend, gender = Gender.FEMALE))

				val myTeamId: Long = persistTeam(Gender.MALE)
				persistMember(myTeamId, me)
				persistMember(myTeamId, friend)
				val opponentTeamId: Long = persistTeam(Gender.FEMALE)
				persistMember(opponentTeamId, opponentOwner)
				persistMember(opponentTeamId, opponentFriend)
				persistMatchedMatch(myTeamId, opponentTeamId)

				// 1단계: me가 떠나 팀은 DISBANDED(매칭은 유지).
				delete("/teams/v1/$myTeamId") { bearer(accessTokenFor(me)) } expect { status(200) }

				// 남은 본인(friend)의 미팅탭: 내 팀은 DISBANDED로 보이고 상대(나간 me)는 null, 매칭된 상대 팀 카드가 그대로 보인다.
				get("/team-matches/v1/meeting-tab") {
					bearer(accessTokenFor(friend))
				} expect {
					status(200)
					body("data.myTeam.teamId", myTeamId.toInt())
					body("data.myTeam.status", "DISBANDED")
					body("data.myTeam.partnerProfileImageCode", nullValue())
					body("data.recommendedTeams", hasSize<Any>(1))
					body("data.recommendedTeams[0].teamId", opponentTeamId.toInt())
				}

				// 상대 팀(opponentOwner)의 미팅탭: 내 DISBANDED 팀이 카드로 그대로 보이고, 남은 구성원(friend 1명)만 노출된다.
				get("/team-matches/v1/meeting-tab") {
					bearer(accessTokenFor(opponentOwner))
				} expect {
					status(200)
					body("data.recommendedTeams", hasSize<Any>(1))
					body("data.recommendedTeams[0].teamId", myTeamId.toInt())
					body("data.recommendedTeams[0].members", hasSize<Any>(1))
					body("data.recommendedTeams[0].members[0].userId", friend.toInt())
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
		IntegrationUtil.deleteAll(QChatMessageEntity.chatMessageEntity)
		IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		IntegrationUtil.deleteAll(QMatchedTeamEntity.matchedTeamEntity)
		IntegrationUtil.deleteAll(QTeamMatchEntity.teamMatchEntity)
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
	}
})
