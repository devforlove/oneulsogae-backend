package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.integration.post
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.RegionEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import java.time.LocalDate

/**
 * `GET /teams/v1/received-invitations` E2E 테스트. (내가 받은 초대 리스트)
 * 요청자가 INVITED 구성원인 INVITING 팀들을 초대자 프로필과 함께 최신순으로 반환한다.
 * 내가 owner(ACTIVE)인 팀·수락(ACTIVE)된 팀·미소속 팀은 제외된다.
 */
class GetReceivedInvitationsE2ETest : AbstractIntegrationSupport({

	// 표시용 프로필을 match_user(닉네임·프로필이미지·나이) + user_details(직업·회사명·키·지역·자기소개)에 저장한다. (성별 MALE 고정)
	fun persistProfile(
		userId: Long,
		nickname: String,
		profileImageCode: String,
		job: String?,
		companyName: String?,
		birthday: LocalDate = LocalDate.of(1998, 1, 1),
		height: Int? = null,
		regionId: Long? = null,
		introduction: String? = null,
	) {
		IntegrationUtil.persist(
			MatchUserEntityFixture.create(userId = userId, gender = Gender.MALE, nickname = nickname, profileImageCode = profileImageCode, birthday = birthday),
		)
		IntegrationUtil.persist(
			UserDetailEntityFixture.create(
				userId = userId,
				nickname = nickname,
				gender = Gender.MALE,
				profileImageCode = profileImageCode,
				job = job,
				companyName = companyName,
				height = height,
				regionId = regionId,
				introduction = introduction,
			),
		)
	}

	// ownerId가 invitedUserId를 초대해 팀을 결성하고 teamId를 돌려준다. (프로필은 미리 저장돼 있어야 한다)
	fun invite(ownerId: Long, invitedUserId: Long): Long =
		post("/teams/v1/invitation") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "region": "서울특별시 강남구", "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
		}.extract().path<Int>("data.teamId").toLong()

	describe("GET /teams/v1/received-invitations") {

		context("초대받은 유저가 여러 초대를 받았으면") {
			it("초대자 프로필을 담아 team id 최신순으로 반환한다 (200)") {
				val me = 4002L
				val ownerA = 4001L
				val ownerB = 4003L
				val gangnamId: Long = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구"),
				).id!!
				persistProfile(me, "나", "1", null, null)
				persistProfile(ownerA, "초대자A", "11", "PM", "토스", birthday = LocalDate.now().minusYears(31))
				persistProfile(ownerB, "초대자B", "22", "디자이너", "카카오", birthday = LocalDate.now().minusYears(33), height = 180, regionId = gangnamId, introduction = "잘 부탁드려요")

				val teamA: Long = invite(ownerA, me)
				val teamB: Long = invite(ownerB, me)

				get("/teams/v1/received-invitations") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("success", true)
					body("data", hasSize<Any>(2))
					// 최신순(team id desc) → [0]=teamB(초대자B), [1]=teamA(초대자A)
					body("data[0].teamId", teamB.toInt())
					body("data[0].name", "우리팀")
					body("data[0].invitedAt", notNullValue())
					// INVITING 팀의 ACTIVE 구성원은 초대자 1명 → participants[0]
					body("data[0].participants", hasSize<Any>(1))
					body("data[0].participants[0].userId", ownerB.toInt())
					body("data[0].participants[0].nickname", "초대자B")
					body("data[0].participants[0].job", "디자이너")
					body("data[0].participants[0].companyName", "카카오")
					body("data[0].participants[0].gender", Gender.MALE.name)
					body("data[0].participants[0].profileImageCode", "22")
					body("data[0].participants[0].age", 33)
					// 상세 시트용 프로필 — 키·지역·자기소개는 user_details에서, 특성·관심사는 미입력이라 빈 배열
					body("data[0].participants[0].height", 180)
					body("data[0].participants[0].activityArea", "서울특별시 강남구")
					body("data[0].participants[0].introduction", "잘 부탁드려요")
					body("data[0].participants[0].traits", hasSize<Any>(0))
					body("data[0].participants[0].interests", hasSize<Any>(0))
					body("data[1].teamId", teamA.toInt())
					body("data[1].participants[0].userId", ownerA.toInt())
					body("data[1].participants[0].nickname", "초대자A")
					body("data[1].participants[0].job", "PM")
					body("data[1].participants[0].companyName", "토스")
					body("data[1].participants[0].profileImageCode", "11")
					body("data[1].participants[0].age", 31)
				}
			}
		}

		context("다른 팀의 owner(ACTIVE)이면서 받은 초대도 있으면") {
			it("내가 INVITED인 팀만 반환하고 내가 owner(ACTIVE)인 팀은 제외한다 (200)") {
				val me = 4501L
				val ownerX = 4502L
				val z = 4503L
				persistProfile(me, "나", "1", null, null)
				persistProfile(ownerX, "초대자X", "1", null, null)
				persistProfile(z, "지", "1", null, null)

				// teamX: ownerX가 나를 초대 → 나는 INVITED. (먼저 해야 아래 teamY 결성 후엔 ALREADY_IN_TEAM으로 막힘)
				val teamX: Long = invite(ownerX, me)
				// teamY: 내가 z를 초대 → 나는 ACTIVE(owner). (나는 INVITED 상태라 활성 팀 제약에 안 걸림)
				invite(me, z)

				get("/teams/v1/received-invitations") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("data", hasSize<Any>(1))
					body("data[0].teamId", teamX.toInt())
					body("data[0].participants[0].userId", ownerX.toInt())
				}
			}
		}

		context("초대자(owner) 본인이 조회하면") {
			it("자신은 INVITED가 아니므로 빈 배열이다 (200)") {
				val owner = 4101L
				val invited = 4102L
				persistProfile(owner, "오너", "1", null, null)
				persistProfile(invited, "초대대상", "1", null, null)
				invite(owner, invited)

				get("/teams/v1/received-invitations") {
					bearer(accessTokenFor(owner))
				} expect {
					status(200)
					body("data", hasSize<Any>(0))
				}
			}
		}

		context("초대를 수락(ACTIVE)한 뒤 조회하면") {
			it("더 이상 INVITED가 아니므로 빈 배열이다 (200)") {
				val owner = 4201L
				val invited = 4202L
				persistProfile(owner, "오너", "1", null, null)
				persistProfile(invited, "초대대상", "1", null, null)
				val teamId: Long = invite(owner, invited)

				post("/teams/v1/$teamId/acceptance") {
					bearer(accessTokenFor(invited))
				} expect {
					status(200)
				}

				get("/teams/v1/received-invitations") {
					bearer(accessTokenFor(invited))
				} expect {
					status(200)
					body("data", hasSize<Any>(0))
				}
			}
		}

		context("받은 초대가 없는 유저가 조회하면") {
			it("빈 배열이다 (200)") {
				val userId = 4301L
				persistProfile(userId, "외톨이", "1", null, null)

				get("/teams/v1/received-invitations") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data", hasSize<Any>(0))
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				get("/teams/v1/received-invitations") expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QRegionEntity.regionEntity)
	}
})
