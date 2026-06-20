package com.org.meeple.core.match.command.domain

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.match.TeamErrorCode
import java.time.LocalDateTime

/**
 * 2:2(팀) 매칭에서 한 편을 이루는 팀 도메인 모델.
 * 팀은 매칭과 무관한 독립 애그리거트로, 구성원을 초대해 먼저 결성([TeamStatus.INVITING])한 뒤 매칭에 배정된다.
 * 구성원 각자의 정보는 [members]([TeamMembers])가 보관한다. (수락은 추후 팀 단위로 다룬다)
 * 영속성은 [com.org.meeple.infra.match.command.entity.TeamEntity](헤더) + [com.org.meeple.infra.match.command.entity.TeamMemberEntity](구성원)가 담당한다.
 */
data class Team(
	val id: Long = 0,
	val name: String,
	val introduction: String? = null,
	val members: TeamMembers,
	val status: TeamStatus = TeamStatus.INVITING,
	val deletedAt: LocalDateTime? = null,
) {

	/**
	 * 초대받은([TeamMemberStatus.INVITED]) 구성원([userId])이 초대를 수락한다.
	 * 그 구성원을 ACTIVE로 전환하고, 전원 ACTIVE가 되면 팀을 [TeamStatus.FORMED]로 전이한 새 모델을 반환한다.
	 * 상태·구성원 자격은 [validateAcceptable]로 검증한다.
	 */
	fun acceptInvitation(userId: Long): Team {
		validateAcceptable(userId)
		val accepted: TeamMembers = members.accept(userId)
		return copy(
			members = accepted,
			status = if (accepted.allActive()) TeamStatus.FORMED else status,
		)
	}

	// INVITING 상태가 아니면 INVALID_TEAM_STATUS, 구성원이 아니면 NOT_TEAM_MEMBER, INVITED 상태가 아니면 NOT_INVITED_MEMBER.
	private fun validateAcceptable(userId: Long) {
		if (status != TeamStatus.INVITING) {
			throw BusinessException(TeamErrorCode.INVALID_TEAM_STATUS)
		}
		val member: TeamMember = members.find(userId)
			?: throw BusinessException(TeamErrorCode.NOT_TEAM_MEMBER)
		if (member.status != TeamMemberStatus.INVITED) {
			throw BusinessException(TeamErrorCode.NOT_INVITED_MEMBER)
		}
	}

	companion object {

		/** 팀 이름 최대 길이. */
		const val MAX_NAME_LENGTH: Int = 50

		/** 팀 소개 최대 길이. */
		const val MAX_INTRODUCTION_LENGTH: Int = 1000

		/**
		 * [ownerId]가 [invitedUserId]를 초대해 팀을 결성한다. (status INVITING)
		 * 초대자([ownerId])는 활성([TeamMemberStatus.ACTIVE]) 구성원으로, 초대 대상([invitedUserId])은 초대중([TeamMemberStatus.INVITED]) 구성원으로 담고,
		 * 각자의 성별([ownerGender]/[invitedGender])을 함께 보관한다.
		 * 이름·소개·자기 초대 여부는 [validateInvite]로 검증한다.
		 */
		fun invite(
			ownerId: Long,
			ownerGender: Gender,
			invitedUserId: Long,
			invitedGender: Gender,
			name: String,
			introduction: String?,
		): Team {
			validateInvite(ownerId, ownerGender, invitedUserId, invitedGender, name, introduction)
			return Team(
				name = name.trim(),
				introduction = introduction,
				members = TeamMembers.of(
					listOf(
						Triple(ownerId, ownerGender, TeamMemberStatus.ACTIVE),
						Triple(invitedUserId, invitedGender, TeamMemberStatus.INVITED),
					),
				),
				status = TeamStatus.INVITING,
			)
		}

		/**
		 * 팀 초대 입력을 검증한다.
		 * 자기 자신을 초대하면 [TeamErrorCode.CANNOT_INVITE_SELF], 초대 대상이 초대자와 다른 성별이면 [TeamErrorCode.MUST_INVITE_SAME_GENDER],
		 * 이름이 비었거나 [MAX_NAME_LENGTH]를 넘으면 [TeamErrorCode.INVALID_TEAM_NAME],
		 * 소개가 [MAX_INTRODUCTION_LENGTH]를 넘으면 [TeamErrorCode.INVALID_TEAM_INTRODUCTION]를 던진다.
		 */
		private fun validateInvite(
			ownerId: Long,
			ownerGender: Gender,
			invitedUserId: Long,
			invitedGender: Gender,
			name: String,
			introduction: String?,
		) {
			if (ownerId == invitedUserId) {
				throw BusinessException(TeamErrorCode.CANNOT_INVITE_SELF)
			}
			if (ownerGender != invitedGender) {
				throw BusinessException(TeamErrorCode.MUST_INVITE_SAME_GENDER)
			}
			val trimmedName: String = name.trim()
			if (trimmedName.isEmpty() || trimmedName.length > MAX_NAME_LENGTH) {
				throw BusinessException(TeamErrorCode.INVALID_TEAM_NAME)
			}
			if (introduction != null && introduction.length > MAX_INTRODUCTION_LENGTH) {
				throw BusinessException(TeamErrorCode.INVALID_TEAM_INTRODUCTION)
			}
		}
	}
}
