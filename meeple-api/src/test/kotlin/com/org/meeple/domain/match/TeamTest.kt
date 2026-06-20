package com.org.meeple.domain.match

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.match.TeamErrorCode
import com.org.meeple.core.match.command.domain.Team
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * [Team] 도메인 유닛 테스트.
 * 프레임워크·인프라 없이 순수 도메인 로직(초대 결성, 입력 검증)을 검증한다.
 */
class TeamTest : DescribeSpec({

	val ownerId: Long = 1L
	val invitedUserId: Long = 2L

	describe("invite - 팀 결성") {
		it("초대자와 초대 대상을 구성원으로 담아 초대중(INVITING) 팀을 결성한다") {
			val team: Team = Team.invite(
				ownerId = ownerId,
				ownerGender = Gender.MALE,
				invitedUserId = invitedUserId,
				invitedGender = Gender.MALE,
				name = "우리팀",
				introduction = "잘 부탁드려요",
			)

			team.status shouldBe TeamStatus.INVITING
			team.name shouldBe "우리팀"
			team.introduction shouldBe "잘 부탁드려요"
			team.members.userIds() shouldContainExactlyInAnyOrder listOf(ownerId, invitedUserId)

			val statusByUserId: Map<Long, TeamMemberStatus> = team.members.values.associate { it.userId to it.status }
			statusByUserId[ownerId] shouldBe TeamMemberStatus.ACTIVE
			statusByUserId[invitedUserId] shouldBe TeamMemberStatus.INVITED
		}

		it("앞뒤 공백을 제거한 이름으로 결성한다") {
			val team: Team = Team.invite(ownerId, Gender.MALE, invitedUserId, Gender.MALE, "  우리팀  ", null)

			team.name shouldBe "우리팀"
		}
	}

	describe("invite - 입력 검증") {
		it("자기 자신을 초대하면 CANNOT_INVITE_SELF를 던진다") {
			val ex: BusinessException = shouldThrow {
				Team.invite(ownerId, Gender.MALE, ownerId, Gender.MALE, "우리팀", null)
			}

			ex.errorCode shouldBe TeamErrorCode.CANNOT_INVITE_SELF
		}

		it("초대 대상이 다른 성별이면 MUST_INVITE_SAME_GENDER를 던진다") {
			val ex: BusinessException = shouldThrow {
				Team.invite(ownerId, Gender.MALE, invitedUserId, Gender.FEMALE, "우리팀", null)
			}

			ex.errorCode shouldBe TeamErrorCode.MUST_INVITE_SAME_GENDER
		}

		it("이름이 공백뿐이면 INVALID_TEAM_NAME을 던진다") {
			val ex: BusinessException = shouldThrow {
				Team.invite(ownerId, Gender.MALE, invitedUserId, Gender.MALE, "   ", null)
			}

			ex.errorCode shouldBe TeamErrorCode.INVALID_TEAM_NAME
		}

		it("이름이 최대 길이를 넘으면 INVALID_TEAM_NAME을 던진다") {
			val ex: BusinessException = shouldThrow {
				Team.invite(ownerId, Gender.MALE, invitedUserId, Gender.MALE, "가".repeat(Team.MAX_NAME_LENGTH + 1), null)
			}

			ex.errorCode shouldBe TeamErrorCode.INVALID_TEAM_NAME
		}

		it("소개가 최대 길이를 넘으면 INVALID_TEAM_INTRODUCTION을 던진다") {
			val ex: BusinessException = shouldThrow {
				Team.invite(ownerId, Gender.MALE, invitedUserId, Gender.MALE, "우리팀", "가".repeat(Team.MAX_INTRODUCTION_LENGTH + 1))
			}

			ex.errorCode shouldBe TeamErrorCode.INVALID_TEAM_INTRODUCTION
		}
	}
})
