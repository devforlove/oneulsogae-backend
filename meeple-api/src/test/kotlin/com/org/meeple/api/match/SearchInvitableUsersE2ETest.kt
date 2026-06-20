package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.match.command.entity.TeamMemberEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasSize

/**
 * `GET /teams/v1/invitable-users` E2E 테스트. (초대 가능 유저 닉네임 검색)
 * 후보는 매칭 가능(match_user 존재)·요청자와 같은 성별이고, 자기 자신·반대 성별·활성 팀 소속·매칭 불가 유저는 제외된다.
 */
class SearchInvitableUsersE2ETest : AbstractIntegrationSupport({

	// 매칭 읽기 모델(match_user, 성별 보유) 행을 저장한다.
	fun persistMatchUser(userId: Long, gender: Gender, nickname: String) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender, nickname = nickname))
	}

	// 프로필 상세(user_details, 닉네임·직업·회사명) 행을 저장한다.
	fun persistUserDetail(userId: Long, gender: Gender, nickname: String, job: String?, companyName: String?) {
		IntegrationUtil.persist(
			UserDetailEntityFixture.create(
				userId = userId,
				nickname = nickname,
				gender = gender,
				job = job,
				companyName = companyName,
			),
		)
	}

	// 활성 팀 구성원 행을 저장한다. (NOT EXISTS 필터 대상 — teamId는 검사에 쓰이지 않아 임의값)
	fun persistActiveTeamMember(userId: Long, gender: Gender) {
		IntegrationUtil.persist(
			TeamMemberEntity(teamId = 1L, userId = userId, gender = gender, status = TeamMemberStatus.ACTIVE),
		)
	}

	describe("GET /teams/v1/invitable-users") {

		context("닉네임이 정확히 일치하는 초대 가능 유저가 있으면") {
			it("같은 성별·매칭가능·팀없음 유저만 id·닉네임·직업·회사명과 함께 반환한다 (200)") {
				val requesterId = 7001L
				val nickname = "홍길동"

				// 요청자: 성별을 match_user에서 읽으므로 match_user 행이 있어야 검색 가능 (닉네임도 동일하지만 자기 자신은 제외돼야 한다)
				persistUserDetail(requesterId, Gender.MALE, nickname, job = "PM", companyName = "내회사")
				persistMatchUser(requesterId, Gender.MALE, nickname)

				// 포함 대상 A, E: 같은 성별(MALE)·매칭가능·팀없음·동일 닉네임 (동명이인 2명)
				persistUserDetail(7002L, Gender.MALE, nickname, job = "개발자", companyName = "토스")
				persistMatchUser(7002L, Gender.MALE, nickname)
				persistUserDetail(7003L, Gender.MALE, nickname, job = null, companyName = null)
				persistMatchUser(7003L, Gender.MALE, nickname)

				// 제외 B: 반대 성별(FEMALE)
				persistUserDetail(7004L, Gender.FEMALE, nickname, job = "디자이너", companyName = "카카오")
				persistMatchUser(7004L, Gender.FEMALE, nickname)

				// 제외 C: 이미 활성 팀 소속
				persistUserDetail(7005L, Gender.MALE, nickname, job = "기획", companyName = "라인")
				persistMatchUser(7005L, Gender.MALE, nickname)
				persistActiveTeamMember(7005L, Gender.MALE)

				// 제외 D: match_user 없음(매칭 불가) — user_details만 존재
				persistUserDetail(7006L, Gender.MALE, nickname, job = "마케터", companyName = "쿠팡")

				// 제외 F: 닉네임 불일치
				persistUserDetail(7007L, Gender.MALE, "임꺽정", job = "개발자", companyName = "배민")
				persistMatchUser(7007L, Gender.MALE, "임꺽정")

				get("/teams/v1/invitable-users?nickname=$nickname") {
					bearer(accessTokenFor(requesterId))
				} expect {
					status(200)
					body("success", true)
					body("data", hasSize<Any>(2))
					body("data.userId", containsInAnyOrder(7002, 7003))
					body("data.job", containsInAnyOrder("개발자", null))
					body("data.companyName", containsInAnyOrder("토스", null))
				}
			}
		}

		context("일치하는 닉네임이 없으면") {
			it("빈 배열을 반환한다 (200)") {
				val requesterId = 7101L
				persistUserDetail(requesterId, Gender.MALE, "내닉네임", job = null, companyName = null)
				persistMatchUser(requesterId, Gender.MALE, "내닉네임")

				get("/teams/v1/invitable-users?nickname=없는닉네임") {
					bearer(accessTokenFor(requesterId))
				} expect {
					status(200)
					body("data", hasSize<Any>(0))
				}
			}
		}

		context("nickname 파라미터가 비어 있으면") {
			it("400을 반환한다") {
				val requesterId = 7201L
				persistUserDetail(requesterId, Gender.MALE, "내닉네임", job = null, companyName = null)

				// 빈 값이면 @NotBlank 검증에 걸린다. (공백 문자열은 RestAssured가 path를 재인코딩해 리터럴로 전달되므로 빈 값으로 검증한다)
				get("/teams/v1/invitable-users?nickname=") {
					bearer(accessTokenFor(requesterId))
				} expect {
					status(400)
					body("success", false)
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				get("/teams/v1/invitable-users?nickname=홍길동") expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
	}
})
