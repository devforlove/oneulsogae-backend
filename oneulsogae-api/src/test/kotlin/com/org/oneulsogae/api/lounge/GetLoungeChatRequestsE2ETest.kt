package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.common.chat.ChatRoomMatchType
import com.org.oneulsogae.common.coin.CoinUsageType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.chat.command.entity.ChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomEntity
import com.org.oneulsogae.infra.fixture.ChatRoomEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.LoungeChatRequestEntityFixture
import com.org.oneulsogae.infra.fixture.LoungePostEntityFixture
import com.org.oneulsogae.infra.fixture.RegionEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.lounge.command.entity.LoungeChatRequestEntity
import com.org.oneulsogae.infra.lounge.command.entity.LoungePostEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungeChatRequestEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungePostEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.region.entity.QRegionEntity
import com.org.oneulsogae.infra.region.entity.RegionEntity
import io.restassured.RestAssured
import org.hamcrest.Matchers
import java.time.LocalDate
import java.time.Period

/**
 * `GET /lounge/v1/chat-requests/received`·`/sent` E2E 테스트.
 * 받은 목록이 내가 쓴 **모든** 셀소를 합산해 최신순으로 내려주는지, 보낸 목록이 내가 신청한 건만 내려주는지,
 * 두 목록의 상대방(partner*) 프로필이 각각 신청자·글 작성자로 채워지는지, 커서 페이징이 이어지는지 검증한다.
 */
class GetLoungeChatRequestsE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		IntegrationUtil.deleteAll(QLoungeChatRequestEntity.loungeChatRequestEntity)
		IntegrationUtil.deleteAll(QLoungePostEntity.loungePostEntity)
		// 프로필이 참조하는 지역까지 정리한다. (regions는 (sido, sigungu) 유니크라 남겨두면 다른 스펙의 같은 지역 생성이 깨진다)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QRegionEntity.regionEntity)
	}

	describe("GET /lounge/v1/chat-requests/received") {

		context("내 셀소 두 건에 신청이 들어오면") {
			it("글을 합산해 최신순으로 내려주고 상대방은 신청자이며 수락된 건만 chatRoomId가 채워진다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-recv-author-1")).id!!
				val olderRequesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-recv-user-1")).id!!
				val newerRequesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-recv-user-2")).id!!
				val birthday: LocalDate = LocalDate.of(1995, 3, 3)
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(
						userId = olderRequesterId,
						nickname = "먼저신청",
						gender = Gender.MALE,
						birthday = birthday,
					),
				)
				val requesterRegion: RegionEntity = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "서울특별시", sigungu = "마포구"),
				)
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(
						userId = newerRequesterId,
						nickname = "나중신청",
						gender = Gender.MALE,
						birthday = birthday,
						profileImageCode = "PROFILE_07",
						regionId = requesterRegion.id,
					),
				)
				// 서로 다른 두 글에 온 신청이 한 목록으로 합산되는지 확인한다.
				val firstPost: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				val secondPost: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				val olderRequest: LoungeChatRequestEntity = IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(
						postId = firstPost.id!!,
						requesterUserId = olderRequesterId,
						receiverUserId = authorId,
						status = LoungeChatRequestStatus.ACCEPTED,
					),
				)
				IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(
						postId = secondPost.id!!,
						requesterUserId = newerRequesterId,
						receiverUserId = authorId,
					),
				)
				val chatRoom: ChatRoomEntity = IntegrationUtil.persist(
					ChatRoomEntityFixture.create(matchType = ChatRoomMatchType.LOUNGE, matchId = olderRequest.id!!),
				)
				val expectedAge: Int = Period.between(birthday, LocalDate.now()).years

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.get("/lounge/v1/chat-requests/received")
					.then()
					.statusCode(200)
					.body("success", Matchers.equalTo(true))
					.body("data.items", Matchers.hasSize<Any>(2))
					// 최신순이라 나중에 신청한 사람이 앞에 온다.
					.body("data.items[0].partnerNickname", Matchers.equalTo("나중신청"))
					.body("data.items[0].partnerUserId", Matchers.equalTo(newerRequesterId.toInt()))
					.body("data.items[0].postId", Matchers.equalTo(secondPost.id!!.toInt()))
					.body("data.items[0].status", Matchers.equalTo("PENDING"))
					.body("data.items[0].chatRoomId", Matchers.nullValue())
					.body("data.items[0].partnerAge", Matchers.equalTo(expectedAge))
					.body("data.items[0].partnerGender", Matchers.equalTo("MALE"))
					.body("data.items[0].partnerProfileImageCode", Matchers.equalTo("PROFILE_07"))
					.body("data.items[0].partnerActivityArea", Matchers.equalTo("서울특별시 마포구"))
					// 지역·프로필 이미지가 없는 상대는 두 필드가 null로 내려간다. (신청 자체는 빠지지 않는다)
					.body("data.items[1].partnerProfileImageCode", Matchers.nullValue())
					.body("data.items[1].partnerActivityArea", Matchers.nullValue())
					.body("data.items[1].partnerNickname", Matchers.equalTo("먼저신청"))
					.body("data.items[1].postId", Matchers.equalTo(firstPost.id!!.toInt()))
					.body("data.items[1].status", Matchers.equalTo("ACCEPTED"))
					.body("data.items[1].chatRoomId", Matchers.equalTo(chatRoom.id!!.toInt()))
					// 수락 버튼의 비용 안내값. 신청마다 다르지 않은 전역 정책값(LOUNGE_CHAT_ACCEPT)이라 응답 루트에 한 번만 실린다.
					.body("data.acceptCoinAmount", Matchers.equalTo(CoinUsageType.LOUNGE_CHAT_ACCEPT.coinAmount))
					.body("data.hasNext", Matchers.equalTo(false))
					.body("data.nextCursor", Matchers.nullValue())
			}
		}

		context("남의 글에 온 신청은") {
			it("내 받은 목록에 섞이지 않는다") {
				val meId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-recv-author-2")).id!!
				val otherAuthorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-recv-author-3")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-recv-user-3")).id!!
				val otherPost: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = otherAuthorId))
				IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(
						postId = otherPost.id!!,
						requesterUserId = requesterId,
						receiverUserId = otherAuthorId,
					),
				)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(meId)}")
					.get("/lounge/v1/chat-requests/received")
					.then()
					.statusCode(200)
					.body("data.items", Matchers.hasSize<Any>(0))
					.body("data.hasNext", Matchers.equalTo(false))
					.body("data.nextCursor", Matchers.nullValue())
			}
		}

		context("받은 신청이 페이지 크기를 넘으면") {
			it("첫 페이지 20건과 커서를 내려주고, 커서로 나머지를 잇는다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-recv-author-4")).id!!
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				// (post_id, requester_user_id) 유니크 제약이 있어 신청자는 각각 다른 사용자여야 한다.
				val requestIds: List<Long> = (1..21).map { index: Int ->
					val requesterId: Long =
						IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-recv-cursor-$index")).id!!
					IntegrationUtil.persist(
						LoungeChatRequestEntityFixture.create(
							postId = post.id!!,
							requesterUserId = requesterId,
							receiverUserId = authorId,
						),
					).id!!
				}
				// 최신순이므로 첫 페이지의 마지막(20번째)은 뒤에서 20번째로 만든 신청이다.
				val expectedCursor: Long = requestIds[requestIds.size - 20]

				val cursor: Int = RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.get("/lounge/v1/chat-requests/received")
					.then()
					.statusCode(200)
					.body("data.items", Matchers.hasSize<Any>(20))
					.body("data.hasNext", Matchers.equalTo(true))
					.body("data.nextCursor", Matchers.equalTo(expectedCursor.toInt()))
					.body("data.items[19].requestId", Matchers.equalTo(expectedCursor.toInt()))
					.extract()
					.path("data.nextCursor")

				// 커서 다음 구간은 남은 가장 오래된 1건이며, 첫 페이지와 겹치지 않는다.
				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.queryParam("cursor", cursor)
					.get("/lounge/v1/chat-requests/received")
					.then()
					.statusCode(200)
					.body("data.items", Matchers.hasSize<Any>(1))
					.body("data.items[0].requestId", Matchers.equalTo(requestIds[0].toInt()))
					.body("data.hasNext", Matchers.equalTo(false))
					.body("data.nextCursor", Matchers.nullValue())
			}
		}
	}

	describe("GET /lounge/v1/chat-requests/sent") {

		context("내가 남의 셀소에 신청했으면") {
			it("보낸 목록에 나오고 상대방은 글 작성자이며 수락 비용은 싣지 않는다") {
				val meId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-sent-user-1")).id!!
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-sent-author-1")).id!!
				val birthday: LocalDate = LocalDate.of(1993, 5, 5)
				val authorRegion: RegionEntity = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "경기도", sigungu = "성남시"),
				)
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(
						userId = authorId,
						nickname = "글쓴이",
						gender = Gender.FEMALE,
						birthday = birthday,
						profileImageCode = "PROFILE_02",
						regionId = authorRegion.id,
					),
				)
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				val request: LoungeChatRequestEntity = IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(
						postId = post.id!!,
						requesterUserId = meId,
						receiverUserId = authorId,
					),
				)
				val expectedAge: Int = Period.between(birthday, LocalDate.now()).years

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(meId)}")
					.get("/lounge/v1/chat-requests/sent")
					.then()
					.statusCode(200)
					.body("data.items", Matchers.hasSize<Any>(1))
					.body("data.items[0].requestId", Matchers.equalTo(request.id!!.toInt()))
					.body("data.items[0].postId", Matchers.equalTo(post.id!!.toInt()))
					// 보낸 목록의 상대방은 글 작성자다.
					.body("data.items[0].partnerUserId", Matchers.equalTo(authorId.toInt()))
					.body("data.items[0].partnerNickname", Matchers.equalTo("글쓴이"))
					.body("data.items[0].partnerGender", Matchers.equalTo("FEMALE"))
					.body("data.items[0].partnerAge", Matchers.equalTo(expectedAge))
					.body("data.items[0].partnerProfileImageCode", Matchers.equalTo("PROFILE_02"))
					.body("data.items[0].partnerActivityArea", Matchers.equalTo("경기도 성남시"))
					.body("data.items[0].status", Matchers.equalTo("PENDING"))
					.body("data.items[0].chatRoomId", Matchers.nullValue())
					// 보낸 신청은 내가 수락하는 것이 아니라 수락 비용을 싣지 않는다.
					.body("data.acceptCoinAmount", Matchers.nullValue())
					.body("data.hasNext", Matchers.equalTo(false))
			}
		}

		context("내가 받은 신청은") {
			it("보낸 목록에 섞이지 않는다") {
				val meId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-sent-user-2")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-sent-user-3")).id!!
				val myPost: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = meId))
				IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(
						postId = myPost.id!!,
						requesterUserId = requesterId,
						receiverUserId = meId,
					),
				)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(meId)}")
					.get("/lounge/v1/chat-requests/sent")
					.then()
					.statusCode(200)
					.body("data.items", Matchers.hasSize<Any>(0))
			}
		}
	}
})
