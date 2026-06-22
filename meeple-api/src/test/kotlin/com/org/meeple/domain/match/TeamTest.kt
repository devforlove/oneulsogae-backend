package com.org.meeple.domain.match

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.match.TeamErrorCode
import com.org.meeple.core.match.command.domain.Team
import com.org.meeple.core.match.command.domain.TeamMember
import com.org.meeple.core.match.command.domain.TeamMembers
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [Team] 도메인 유닛 테스트.
 * 프레임워크·인프라 없이 순수 도메인 로직(초대 결성, 입력 검증)을 검증한다.
 */
class TeamTest : DescribeSpec({

	val ownerId: Long = 1L
	val invitedUserId: Long = 2L
	val validIntroduction: String = "함께 즐겁게 활동할 팀이에요"

	describe("invite - 팀 결성") {
		it("초대자와 초대 대상을 구성원으로 담아 초대중(INVITING) 팀을 결성한다") {
			val team: Team = Team.invite(
				ownerId = ownerId,
				ownerGender = Gender.MALE,
				invitedUserId = invitedUserId,
				invitedGender = Gender.MALE,
				name = "우리팀",
				introduction = validIntroduction,
				region = "서울특별시 강남구",
			)

			team.status shouldBe TeamStatus.INVITING
			team.name shouldBe "우리팀"
			team.introduction shouldBe validIntroduction
			// 활동지역과 그로부터 산출한 권역 코드를 보관한다. (서울 → 1)
			team.region shouldBe "서울특별시 강남구"
			team.regionCode shouldBe 1
			team.members.userIds() shouldContainExactlyInAnyOrder listOf(ownerId, invitedUserId)

			val statusByUserId: Map<Long, TeamMemberStatus> = team.members.values.associate { it.userId to it.status }
			statusByUserId[ownerId] shouldBe TeamMemberStatus.ACTIVE
			statusByUserId[invitedUserId] shouldBe TeamMemberStatus.INVITED
		}

		it("앞뒤 공백을 제거한 이름·소개로 결성한다") {
			val team: Team = Team.invite(ownerId, Gender.MALE, invitedUserId, Gender.MALE, "  우리팀  ", "  $validIntroduction  ", "  서울특별시 강남구  ")

			team.name shouldBe "우리팀"
			team.introduction shouldBe validIntroduction
			team.region shouldBe "서울특별시 강남구"
		}

		it("인식할 수 없는 활동지역이면 INVALID_TEAM_REGION을 던진다") {
			val ex: BusinessException = shouldThrow {
				Team.invite(ownerId, Gender.MALE, invitedUserId, Gender.MALE, "우리팀", validIntroduction, "알 수 없는 지역")
			}

			ex.errorCode shouldBe TeamErrorCode.INVALID_TEAM_REGION
		}
	}

	describe("invite - 입력 검증") {
		it("자기 자신을 초대하면 CANNOT_INVITE_SELF를 던진다") {
			val ex: BusinessException = shouldThrow {
				Team.invite(ownerId, Gender.MALE, ownerId, Gender.MALE, "우리팀", validIntroduction, "서울특별시 강남구")
			}

			ex.errorCode shouldBe TeamErrorCode.CANNOT_INVITE_SELF
		}

		it("초대 대상이 다른 성별이면 MUST_INVITE_SAME_GENDER를 던진다") {
			val ex: BusinessException = shouldThrow {
				Team.invite(ownerId, Gender.MALE, invitedUserId, Gender.FEMALE, "우리팀", validIntroduction, "서울특별시 강남구")
			}

			ex.errorCode shouldBe TeamErrorCode.MUST_INVITE_SAME_GENDER
		}

		it("이름이 공백뿐이면 INVALID_TEAM_NAME을 던진다") {
			val ex: BusinessException = shouldThrow {
				Team.invite(ownerId, Gender.MALE, invitedUserId, Gender.MALE, "   ", validIntroduction, "서울특별시 강남구")
			}

			ex.errorCode shouldBe TeamErrorCode.INVALID_TEAM_NAME
		}

		it("이름이 최대 길이를 넘으면 INVALID_TEAM_NAME을 던진다") {
			val ex: BusinessException = shouldThrow {
				Team.invite(ownerId, Gender.MALE, invitedUserId, Gender.MALE, "가".repeat(Team.MAX_NAME_LENGTH + 1), validIntroduction, "서울특별시 강남구")
			}

			ex.errorCode shouldBe TeamErrorCode.INVALID_TEAM_NAME
		}

		it("소개가 최소 길이 미만이면 INVALID_TEAM_INTRODUCTION을 던진다") {
			val ex: BusinessException = shouldThrow {
				Team.invite(ownerId, Gender.MALE, invitedUserId, Gender.MALE, "우리팀", "가".repeat(Team.MIN_INTRODUCTION_LENGTH - 1), "서울특별시 강남구")
			}

			ex.errorCode shouldBe TeamErrorCode.INVALID_TEAM_INTRODUCTION
		}

		it("소개가 최대 길이를 넘으면 INVALID_TEAM_INTRODUCTION을 던진다") {
			val ex: BusinessException = shouldThrow {
				Team.invite(ownerId, Gender.MALE, invitedUserId, Gender.MALE, "우리팀", "가".repeat(Team.MAX_INTRODUCTION_LENGTH + 1), "서울특별시 강남구")
			}

			ex.errorCode shouldBe TeamErrorCode.INVALID_TEAM_INTRODUCTION
		}
	}

	describe("acceptInvitation - 초대 수락") {
		fun invitingTeam(): Team =
			Team(
				name = "우리팀",
				gender = Gender.MALE,
				region = "서울특별시 강남구",
				regionCode = 1,
				members = TeamMembers(
					listOf(
						TeamMember(teamId = 0, userId = ownerId, status = TeamMemberStatus.ACTIVE),
						TeamMember(teamId = 0, userId = invitedUserId, status = TeamMemberStatus.INVITED),
					),
				),
				status = TeamStatus.INVITING,
			)

		it("초대받은 구성원이 수락하면 ACTIVE가 되고 전원 ACTIVE이므로 ACTIVE로 전이한다") {
			val formed: Team = invitingTeam().acceptInvitation(invitedUserId)

			formed.status shouldBe TeamStatus.ACTIVE
			formed.members.find(invitedUserId)!!.status shouldBe TeamMemberStatus.ACTIVE
		}

		it("INVITING이 아니면 INVALID_TEAM_STATUS를 던진다") {
			val ex: BusinessException = shouldThrow {
				invitingTeam().copy(status = TeamStatus.ACTIVE).acceptInvitation(invitedUserId)
			}

			ex.errorCode shouldBe TeamErrorCode.INVALID_TEAM_STATUS
		}

		it("구성원이 아니면 NOT_TEAM_MEMBER를 던진다") {
			val ex: BusinessException = shouldThrow { invitingTeam().acceptInvitation(999L) }

			ex.errorCode shouldBe TeamErrorCode.NOT_TEAM_MEMBER
		}

		it("이미 ACTIVE인(초대받지 않은) 구성원이 수락하면 NOT_INVITED_MEMBER를 던진다") {
			val ex: BusinessException = shouldThrow { invitingTeam().acceptInvitation(ownerId) }

			ex.errorCode shouldBe TeamErrorCode.NOT_INVITED_MEMBER
		}
	}

	describe("withdrawInvitation - 거절·초대취소") {
		val now: LocalDateTime = LocalDateTime.of(2026, 6, 20, 12, 0)

		fun invitingTeam(): Team =
			Team(
				name = "우리팀",
				gender = Gender.MALE,
				region = "서울특별시 강남구",
				regionCode = 1,
				members = TeamMembers(
					listOf(
						TeamMember(teamId = 0, userId = ownerId, status = TeamMemberStatus.ACTIVE),
						TeamMember(teamId = 0, userId = invitedUserId, status = TeamMemberStatus.INVITED),
					),
				),
				status = TeamStatus.INVITING,
			)

		it("INVITING 팀의 구성원이 철회하면 DEACTIVATED + 전원 비활성·soft delete가 된다") {
			val deactivated: Team = invitingTeam().withdrawInvitation(ownerId, now)

			deactivated.status shouldBe TeamStatus.DEACTIVATED
			deactivated.deletedAt shouldBe now
			deactivated.members.values.forEach { it.status shouldBe TeamMemberStatus.DEACTIVE }
		}

		it("INVITING이 아니면 INVALID_TEAM_STATUS를 던진다") {
			val ex: BusinessException = shouldThrow {
				invitingTeam().copy(status = TeamStatus.ACTIVE).withdrawInvitation(ownerId, now)
			}
			ex.errorCode shouldBe TeamErrorCode.INVALID_TEAM_STATUS
		}

		it("구성원이 아니면 NOT_TEAM_MEMBER를 던진다") {
			val ex: BusinessException = shouldThrow { invitingTeam().withdrawInvitation(999L, now) }
			ex.errorCode shouldBe TeamErrorCode.NOT_TEAM_MEMBER
		}
	}

	describe("disband - 해체·떠나기") {
		val now: LocalDateTime = LocalDateTime.of(2026, 6, 20, 12, 0)

		fun formedTeam(): Team =
			Team(
				name = "우리팀",
				gender = Gender.MALE,
				region = "서울특별시 강남구",
				regionCode = 1,
				members = TeamMembers(
					listOf(
						TeamMember(teamId = 0, userId = ownerId, status = TeamMemberStatus.ACTIVE),
						TeamMember(teamId = 0, userId = invitedUserId, status = TeamMemberStatus.ACTIVE),
					),
				),
				status = TeamStatus.ACTIVE,
			)

		it("ACTIVE 팀의 구성원이 해체하면 DEACTIVATED + soft delete가 된다") {
			val deactivated: Team = formedTeam().disband(invitedUserId, now)

			deactivated.status shouldBe TeamStatus.DEACTIVATED
			deactivated.deletedAt shouldBe now
		}

		it("ACTIVE가 아니면 INVALID_TEAM_STATUS를 던진다") {
			val ex: BusinessException = shouldThrow {
				formedTeam().copy(status = TeamStatus.INVITING).disband(ownerId, now)
			}
			ex.errorCode shouldBe TeamErrorCode.INVALID_TEAM_STATUS
		}
	}
})
