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
	/** 팀 성별. 팀은 동성으로 구성되므로 팀 단위로 하나의 성별을 가진다. (구성원은 성별을 따로 보관하지 않는다) */
	val gender: Gender,
	/** 팀 활동지역 id(regions FK). 초대 시 regionId로 받는다. (표시용 지역명은 응답 시 regions join으로 내려준다) */
	val regionId: Long,
	val introduction: String? = null,
	val members: TeamMembers,
	val status: TeamStatus = TeamStatus.INVITING,
	val deletedAt: LocalDateTime? = null,
) {

	/**
	 * 초대받은([TeamMemberStatus.INVITED]) 구성원([userId])이 초대를 수락한다.
	 * 그 구성원을 ACTIVE로 전환하고, 전원 ACTIVE가 되면 팀을 [TeamStatus.ACTIVE]로 전이한 새 모델을 반환한다.
	 * 상태·구성원 자격은 [validateAcceptable]로 검증한다.
	 */
	fun acceptInvitation(userId: Long): Team {
		validateAcceptable(userId)
		val accepted: TeamMembers = members.accept(userId)
		return copy(
			members = accepted,
			status = if (accepted.allActive()) TeamStatus.ACTIVE else status,
		)
	}

	/** 초대 단계(INVITING) 팀의 초대자(ACTIVE 구성원, owner) userId. */
	fun inviterId(): Long = members.inviterId()

	/** 초대 단계(INVITING) 팀의 초대 대상(INVITED 구성원) userId. */
	fun invitedId(): Long = members.invitedId()

	/**
	 * 초대 단계([TeamStatus.INVITING])의 팀을 철회한다. (초대받은 사람의 거절 / 초대자의 취소 공통)
	 * 팀을 [TeamStatus.DEACTIVATED]로 전이하고 팀·구성원을 [now]로 소프트 삭제한 새 모델을 반환한다.
	 */
	fun withdrawInvitation(userId: Long, now: LocalDateTime): Team {
		validateWithdrawable(userId)
		return deactivate(now)
	}

	/**
	 * 결성([TeamStatus.ACTIVE])된 팀을 해체한다. (구성원이 떠나면 2인 팀이 유지될 수 없어 팀 전체를 비활성화)
	 * 팀을 [TeamStatus.DEACTIVATED]로 전이하고 팀·구성원을 [now]로 소프트 삭제한 새 모델을 반환한다.
	 */
	fun disband(userId: Long, now: LocalDateTime): Team {
		validateDisbandable(userId)
		return deactivate(now)
	}

	// INVITING 상태가 아니면 INVALID_TEAM_STATUS, 구성원이 아니면 NOT_TEAM_MEMBER.
	private fun validateWithdrawable(userId: Long) {
		if (status != TeamStatus.INVITING) {
			throw BusinessException(TeamErrorCode.INVALID_TEAM_STATUS)
		}
		if (!members.isMember(userId)) {
			throw BusinessException(TeamErrorCode.NOT_TEAM_MEMBER)
		}
	}

	// ACTIVE 상태가 아니면 INVALID_TEAM_STATUS, 구성원이 아니면 NOT_TEAM_MEMBER.
	private fun validateDisbandable(userId: Long) {
		if (status != TeamStatus.ACTIVE) {
			throw BusinessException(TeamErrorCode.INVALID_TEAM_STATUS)
		}
		if (!members.isMember(userId)) {
			throw BusinessException(TeamErrorCode.NOT_TEAM_MEMBER)
		}
	}

	// 팀과 구성원을 비활성·소프트 삭제한다. (withdraw/disband 공통)
	private fun deactivate(now: LocalDateTime): Team =
		copy(status = TeamStatus.DEACTIVATED, deletedAt = now, members = members.deactivateAll(now))

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

		/** 팀 소개 최소 길이. */
		const val MIN_INTRODUCTION_LENGTH: Int = 10

		/** 팀 소개 최대 길이. (100자 미만 = 99자 이하) */
		const val MAX_INTRODUCTION_LENGTH: Int = 99

		/**
		 * [ownerId]가 [invitedUserId]를 초대해 팀을 결성한다. (status INVITING)
		 * 초대자([ownerId])는 활성([TeamMemberStatus.ACTIVE]) 구성원으로, 초대 대상([invitedUserId])은 초대중([TeamMemberStatus.INVITED]) 구성원으로 담는다.
		 * 팀 성별은 초대자 성별([ownerGender])로, 활동지역은 [regionId](regions FK)로 보관한다. (regionId 유효성은 서비스가 검증)
		 * 이름·소개·자기 초대 여부는 [validateInvite]로 검증한다.
		 */
		fun invite(
			ownerId: Long,
			ownerGender: Gender,
			invitedUserId: Long,
			invitedGender: Gender,
			name: String,
			introduction: String,
			regionId: Long,
		): Team {
			validateInvite(ownerId, ownerGender, invitedUserId, invitedGender, name, introduction)
			return Team(
				name = name.trim(),
				// 팀은 동성 구성이라 팀 성별 = 초대자 성별. (initiator/invited 동일 성별은 validateInvite에서 보장)
				gender = ownerGender,
				regionId = regionId,
				introduction = introduction.trim(),
				members = TeamMembers.of(
					listOf(
						ownerId to TeamMemberStatus.ACTIVE,
						invitedUserId to TeamMemberStatus.INVITED,
					),
				),
				status = TeamStatus.INVITING,
			)
		}

		/**
		 * 팀 초대 입력을 검증한다.
		 * 자기 자신을 초대하면 [TeamErrorCode.CANNOT_INVITE_SELF], 초대 대상이 초대자와 다른 성별이면 [TeamErrorCode.MUST_INVITE_SAME_GENDER],
		 * 이름이 비었거나 [MAX_NAME_LENGTH]를 넘으면 [TeamErrorCode.INVALID_TEAM_NAME],
		 * 소개가 [MIN_INTRODUCTION_LENGTH]자 미만이거나 [MAX_INTRODUCTION_LENGTH]자를 넘으면 [TeamErrorCode.INVALID_TEAM_INTRODUCTION]를 던진다.
		 */
		private fun validateInvite(
			ownerId: Long,
			ownerGender: Gender,
			invitedUserId: Long,
			invitedGender: Gender,
			name: String,
			introduction: String,
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
			val trimmedIntroduction: String = introduction.trim()
			if (trimmedIntroduction.length < MIN_INTRODUCTION_LENGTH || trimmedIntroduction.length > MAX_INTRODUCTION_LENGTH) {
				throw BusinessException(TeamErrorCode.INVALID_TEAM_INTRODUCTION)
			}
		}
	}
}
