package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.LoungeChatRequestEntityFixture
import com.org.oneulsogae.infra.fixture.LoungePostEntityFixture
import com.org.oneulsogae.infra.fixture.LoungePostImageEntityFixture
import com.org.oneulsogae.infra.fixture.RegionEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.lounge.command.entity.LoungePostEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungeChatRequestEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungePostEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungePostImageEntity
import com.org.oneulsogae.infra.region.entity.QRegionEntity
import com.org.oneulsogae.infra.region.entity.RegionEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import io.restassured.RestAssured
import org.hamcrest.Matchers
import java.time.LocalDate
import java.time.Period

/**
 * `GET /lounge/v1/self-intro-posts` E2E 테스트.
 * 라운지 그리드용 목록을 최신순 24개씩 커서 페이징으로 내려주는지, 좋아요 수·작성자 닉네임·
 * 대표 사진 열람용 URL이 실리는지, 사진이 없는 글도 빠지지 않는지 검증한다.
 * (presigned URL은 [com.org.oneulsogae.common.config.TestFileStorageConfig]의 페이크로 대체)
 */
class GetSelfIntroPostsE2ETest : AbstractIntegrationSupport({

	beforeSpec {
		IntegrationUtil.deleteAll(QLoungeChatRequestEntity.loungeChatRequestEntity)
		IntegrationUtil.deleteAll(QLoungePostImageEntity.loungePostImageEntity)
		IntegrationUtil.deleteAll(QLoungePostEntity.loungePostEntity)
	}

	// 배지 케이스가 만든 신청 행을 정리한다. (같은 글에 두 번 신청할 수 없어 다음 실행에 걸리지 않도록)
	afterTest {
		IntegrationUtil.deleteAll(QLoungeChatRequestEntity.loungeChatRequestEntity)
		// 프로필이 참조하는 지역까지 정리한다. (regions는 (sido, sigungu) 유니크라 남겨두면 다른 스펙의 같은 지역 생성이 깨진다)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QRegionEntity.regionEntity)
	}

	describe("GET /lounge/v1/self-intro-posts") {

		context("셀소 26건이 있으면") {
			it("최신순 24건과 다음 커서를 내려주고, 커서로 나머지 2건을 잇는다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-1")).id!!
				val birthday: LocalDate = LocalDate.of(1994, 2, 2)
				val region: RegionEntity = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "인천광역시", sigungu = "연수구"),
				)
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(
						userId = userId,
						nickname = "라운지주민",
						regionId = region.id,
						gender = Gender.FEMALE,
						birthday = birthday,
						profileImageCode = "PROFILE_03",
						job = "기획자",
						companyName = "오늘소개",
						universityName = "오늘대학교",
					),
				)
				val expectedAge: Int = Period.between(birthday, LocalDate.now()).years
				val postIds: List<Long> = (1..26).map { index: Int ->
					val post: LoungePostEntity = IntegrationUtil.persist(
						LoungePostEntityFixture.create(userId = userId, likeCount = index),
					)
					IntegrationUtil.persist(
						LoungePostImageEntityFixture.create(
							postId = post.id!!,
							imageKey = "lounge-posts/$userId/$index.jpg",
						),
					)
					post.id!!
				}
				val latestPostId: Long = postIds.last()
				val cursorPostId: Long = postIds[postIds.size - 24] // 첫 페이지의 마지막(24번째) 글

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.get("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(200)
					.body("success", Matchers.equalTo(true))
					.body("data.items", Matchers.hasSize<Any>(24))
					.body("data.hasNext", Matchers.equalTo(true))
					.body("data.nextCursor", Matchers.equalTo(cursorPostId.toInt()))
					.body("data.items[0].postId", Matchers.equalTo(latestPostId.toInt()))
					.body("data.items[0].authorNickname", Matchers.equalTo("라운지주민"))
					.body("data.items[0].likeCount", Matchers.equalTo(26))
					.body("data.items[0].imageUrl", Matchers.equalTo("https://presigned.test/lounge-posts/$userId/26.jpg"))
					.body("data.items[0].authorGender", Matchers.equalTo("FEMALE"))
					.body("data.items[0].authorAge", Matchers.equalTo(expectedAge))
					.body("data.items[0].authorProfileImageCode", Matchers.equalTo("PROFILE_03"))
					.body("data.items[0].authorJob", Matchers.equalTo("기획자"))
					.body("data.items[0].authorCompanyName", Matchers.equalTo("오늘소개"))
					.body("data.items[0].authorUniversityName", Matchers.equalTo("오늘대학교"))
					.body("data.items[0].authorActivityArea", Matchers.equalTo("인천광역시 연수구"))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.queryParam("cursor", cursorPostId)
					.get("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(200)
					.body("data.items", Matchers.hasSize<Any>(2))
					.body("data.hasNext", Matchers.equalTo(false))
					.body("data.nextCursor", Matchers.nullValue())
					.body("data.items[0].postId", Matchers.equalTo(postIds[1].toInt()))
			}
		}

		context("사진이 없는 글이 있으면") {
			it("목록에서 빠지지 않고 imageUrl만 null로 내려간다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-2")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, nickname = "사진없음"))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = userId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.get("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(200)
					.body("data.items[0].postId", Matchers.equalTo(post.id!!.toInt()))
					.body("data.items[0].authorNickname", Matchers.equalTo("사진없음"))
					.body("data.items[0].imageUrl", Matchers.nullValue())
					// 직업·회사·학교·프로필 이미지를 설정하지 않은 작성자는 해당 필드만 null로 내려간다. (글은 목록에서 빠지지 않는다)
					.body("data.items[0].authorJob", Matchers.nullValue())
					.body("data.items[0].authorCompanyName", Matchers.nullValue())
					.body("data.items[0].authorUniversityName", Matchers.nullValue())
					.body("data.items[0].authorProfileImageCode", Matchers.nullValue())
					.body("data.items[0].authorActivityArea", Matchers.nullValue())
			}
		}

		context("내 셀소 두 건에 미수락 신청 3건과 수락된 신청 1건이 있으면") {
			it("받은 배지는 미수락 3건만 세고, 남의 글에 온 신청은 세지 않는다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-badge-1")).id!!
				val otherAuthorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-badge-2")).id!!
				val firstPost: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				val secondPost: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				val otherPost: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = otherAuthorId))

				// 내 글 두 건에 걸쳐 미수락 3건. (합산되는지 확인)
				val requesterIds: List<Long> = (1..3).map { index: Int ->
					IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-badge-req-$index")).id!!
				}
				IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(postId = firstPost.id!!, requesterUserId = requesterIds[0], receiverUserId = authorId),
				)
				IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(postId = firstPost.id!!, requesterUserId = requesterIds[1], receiverUserId = authorId),
				)
				IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(postId = secondPost.id!!, requesterUserId = requesterIds[2], receiverUserId = authorId),
				)
				// 이미 수락한 신청은 배지에서 빠진다.
				val acceptedRequesterId: Long =
					IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-badge-req-4")).id!!
				IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(
						postId = secondPost.id!!,
						requesterUserId = acceptedRequesterId,
						receiverUserId = authorId,
						status = LoungeChatRequestStatus.ACCEPTED,
					),
				)
				// 남의 글에 온 신청은 내 배지와 무관하다.
				IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(postId = otherPost.id!!, requesterUserId = requesterIds[0], receiverUserId = otherAuthorId),
				)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.get("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(200)
					.body("data.receivedPendingChatRequestCount", Matchers.equalTo(3))

				// 신청자 관점: 남의 글에 신청했을 뿐이라 받은 배지는 0이다.
				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(requesterIds[0])}")
					.get("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(200)
					.body("data.receivedPendingChatRequestCount", Matchers.equalTo(0))
			}
		}

		context("요청자 프로필에 회사명이 있으면") {
			it("companyVerified=true를 내려준다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-verified")).id!!
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(
						userId = userId,
						nickname = "인증회원",
						gender = Gender.FEMALE,
						birthday = LocalDate.of(1995, 5, 5),
						companyName = "오늘소개",
					),
				)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.get("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(200)
					.body("data.companyVerified", Matchers.equalTo(true))
			}
		}

		context("요청자 프로필에 회사명이 없으면") {
			it("companyVerified=false를 내려준다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-unverified")).id!!
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(
						userId = userId,
						nickname = "미인증회원",
						gender = Gender.FEMALE,
						birthday = LocalDate.of(1995, 5, 5),
						companyName = null,
					),
				)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.get("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(200)
					.body("data.companyVerified", Matchers.equalTo(false))
			}
		}

		context("토큰 없이 조회하면") {
			it("200으로 목록을 내려주고, 개인화 필드는 0/false다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-anon")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, nickname = "공개유저"))
				IntegrationUtil.persist(LoungePostEntityFixture.create(userId = userId))

				RestAssured.given()
					.get("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(200)
					.body("success", Matchers.equalTo(true))
					.body("data.items.size()", Matchers.greaterThanOrEqualTo(1))
					.body("data.receivedPendingChatRequestCount", Matchers.equalTo(0))
					.body("data.companyVerified", Matchers.equalTo(false))
			}
		}
	}
})
