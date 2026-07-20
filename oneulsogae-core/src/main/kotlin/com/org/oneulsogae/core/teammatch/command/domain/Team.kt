package com.org.oneulsogae.core.teammatch.command.domain

import com.org.oneulsogae.common.match.TeamMemberStatus
import com.org.oneulsogae.common.match.TeamStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.teammatch.TeamErrorCode
import java.time.LocalDateTime

/**
 * 2:2(팀) 매칭에서 한 편을 이루는 팀 도메인 모델.
 * 팀은 매칭과 무관한 독립 애그리거트로, 구성원을 초대해 먼저 결성([TeamStatus.INVITING])한 뒤 매칭에 배정된다.
 * 구성원 각자의 정보는 [members]([TeamMembers])가 보관한다. (수락은 추후 팀 단위로 다룬다)
 * 영속성은 [com.org.oneulsogae.infra.teammatch.command.entity.TeamEntity](헤더) + [com.org.oneulsogae.infra.teammatch.command.entity.TeamMemberEntity](구성원)가 담당한다.
 */
data class Team(
	val id: Long = 0,
	/** 낙관적 락 버전. 영속성 계층이 같은 팀 행의 동시 변경(수락↔철회 등)을 이 값으로 감지한다. (읽은 시점 값을 실어 저장) */
	val version: Long = 0,
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

	/** 활성(ACTIVE) 구성원 목록. (결성([TeamStatus.ACTIVE])된 팀이면 전원. 각 구성원은 소속 teamId를 보유) */
	fun activeMembers(): List<TeamMember> = members.activeMembers()

	/** 활성(ACTIVE) 구성원의 userId 목록. (결성([TeamStatus.ACTIVE])된 팀이면 전원) */
	fun activeMemberIds(): List<Long> = members.activeMemberIds()

	/** 구성원들에 이 팀의 id([teamId])를 채워 반환한다. (영속화 직전, 헤더 저장으로 id를 얻은 뒤 호출) */
	fun membersWith(teamId: Long): TeamMembers = members.withTeamId(teamId)

	/**
	 * 초대 단계([TeamStatus.INVITING])의 팀을 철회한다. (초대받은 사람의 거절 / 초대자의 취소 공통)
	 * 팀을 [TeamStatus.DEACTIVATED]로 전이하고 팀·구성원을 [now]로 소프트 삭제한 새 모델을 반환한다.
	 */
	fun withdrawInvitation(userId: Long, now: LocalDateTime): Team {
		validateWithdrawable(userId)
		return deactivate(now)
	}

	/**
	 * 결성([TeamStatus.ACTIVE]) 또는 해체중([TeamStatus.DISBANDED]) 팀에서 구성원([userId]) 한 명이 떠난다.
	 * 떠나는 구성원만 비활성(DEACTIVE)·소프트 삭제하고, 팀 상태는 남는 활성 구성원 유무로 갈린다:
	 * - 남은 활성 구성원이 있으면 팀을 [TeamStatus.DISBANDED]로 전이한다. (헤더는 소프트 삭제하지 않아 남은 구성원이 마저 떠날 수 있다)
	 * - 마지막 구성원이면 팀을 [TeamStatus.DEACTIVATED]로 전이하고 팀 헤더까지 [now]로 소프트 삭제한다.
	 * 상태·구성원 자격은 [validateDisbandable]로 검증한다.
	 */
	fun disband(userId: Long, now: LocalDateTime): Team {
		validateDisbandable(userId)
		val remaining: TeamMembers = members.deactivate(userId, now)
		return if (members.hasActiveMemberExcept(userId)) {
			copy(status = TeamStatus.DISBANDED, members = remaining)
		} else {
			copy(status = TeamStatus.DEACTIVATED, deletedAt = now, members = remaining)
		}
	}

	/**
	 * 팀의 표시 정보(이름·소개·활동지역)를 수정한 새 모델을 반환한다. (성별·구성원·상태는 바꾸지 않는다)
	 * 이름·소개는 앞뒤 공백을 제거해 반영한다. (이름·소개 형식은 요청에서 Bean Validation으로 검증)
	 * 상태·구성원 자격은 [validateUpdatable]로 검증한다.
	 */
	fun update(userId: Long, name: String, introduction: String, regionId: Long): Team {
		validateUpdatable(userId)
		return copy(name = name.trim(), introduction = introduction.trim(), regionId = regionId)
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

	// INVITING/ACTIVE 상태가 아니면 INVALID_TEAM_STATUS, 구성원이 아니면 NOT_TEAM_MEMBER.
	private fun validateUpdatable(userId: Long) {
		if (status != TeamStatus.INVITING && status != TeamStatus.ACTIVE) {
			throw BusinessException(TeamErrorCode.INVALID_TEAM_STATUS)
		}
		if (!members.isMember(userId)) {
			throw BusinessException(TeamErrorCode.NOT_TEAM_MEMBER)
		}
	}

	// ACTIVE/DISBANDED 상태가 아니면 INVALID_TEAM_STATUS, 활성(ACTIVE) 구성원이 아니면 NOT_TEAM_MEMBER. (이미 떠난 구성원의 재호출 차단)
	private fun validateDisbandable(userId: Long) {
		if (status != TeamStatus.ACTIVE && status != TeamStatus.DISBANDED) {
			throw BusinessException(TeamErrorCode.INVALID_TEAM_STATUS)
		}
		val member: TeamMember = members.find(userId)
			?: throw BusinessException(TeamErrorCode.NOT_TEAM_MEMBER)
		if (member.status != TeamMemberStatus.ACTIVE) {
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

		/**
		 * [ownerId]가 [invitedUserId]를 초대해 팀을 결성한다. (status INVITING)
		 * 초대자([ownerId])는 활성([TeamMemberStatus.ACTIVE]) 구성원으로, 초대 대상([invitedUserId])은 초대중([TeamMemberStatus.INVITED]) 구성원으로 담는다.
		 * 팀 성별은 초대자 성별([ownerGender])로, 활동지역은 [regionId](regions FK)로 보관한다. (regionId 유효성은 서비스가 검증)
		 * 자기 초대·동성 여부는 [validateInvite]로 검증한다. (이름·소개 형식은 요청에서 Bean Validation으로 검증)
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
			validateInvite(ownerId, ownerGender, invitedUserId, invitedGender)
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
		 * 팀 초대의 비즈니스 규칙을 검증한다. (이름·소개 형식은 요청(Bean Validation)에서 검증한다)
		 * 자기 자신을 초대하면 [TeamErrorCode.CANNOT_INVITE_SELF], 초대 대상이 초대자와 다른 성별이면 [TeamErrorCode.MUST_INVITE_SAME_GENDER]를 던진다.
		 */
		private fun validateInvite(
			ownerId: Long,
			ownerGender: Gender,
			invitedUserId: Long,
			invitedGender: Gender,
		) {
			if (ownerId == invitedUserId) {
				throw BusinessException(TeamErrorCode.CANNOT_INVITE_SELF)
			}
			if (ownerGender != invitedGender) {
				throw BusinessException(TeamErrorCode.MUST_INVITE_SAME_GENDER)
			}
		}
	}
}
