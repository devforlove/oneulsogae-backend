package com.org.meeple.api.match.response

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.query.dto.ReceivedInvitation
import com.org.meeple.core.match.query.dto.ReceivedInvitationInviter
import java.time.LocalDateTime

/**
 * 내가 받은 초대 한 건 응답. 팀 메타와 내가 초대된 시각, 그 팀의 ACTIVE 구성원(=초대한 상대방) 목록을 담는다.
 * [inviters]는 생성 순으로, 2:2에서는 1명이지만 팀이 커지면 여러 명일 수 있어 리스트다.
 */
data class ReceivedInvitationResponse(
	val teamId: Long,
	val name: String,
	val introduction: String?,
	val invitedAt: LocalDateTime,
	val inviters: List<Inviter>,
) {

	/**
	 * 초대 팀의 ACTIVE 구성원 프로필. 닉네임·직업·회사명·성별·프로필이미지·나이에 더해
	 * 카드 상세 시트용 키·지역·자기소개·특성·관심사를 담는다. (비공개면 null/빈 배열)
	 */
	data class Inviter(
		val userId: Long,
		val nickname: String,
		val job: String?,
		val companyName: String?,
		val gender: Gender,
		val profileImageCode: String,
		val age: Int,
		val height: Int?,
		val activityArea: String?,
		val introduction: String?,
		val traits: List<String>,
		val interests: List<String>,
	)

	companion object {
		fun of(invitation: ReceivedInvitation): ReceivedInvitationResponse =
			ReceivedInvitationResponse(
				teamId = invitation.teamId,
				name = invitation.name,
				introduction = invitation.introduction,
				invitedAt = invitation.invitedAt,
				inviters = invitation.inviters.map { inviter: ReceivedInvitationInviter ->
					Inviter(
						userId = inviter.userId,
						nickname = inviter.nickname,
						job = inviter.job,
						companyName = inviter.companyName,
						gender = inviter.gender,
						profileImageCode = inviter.profileImageCode,
						age = inviter.age,
						height = inviter.height,
						activityArea = inviter.activityArea,
						introduction = inviter.introduction,
						traits = inviter.traits,
						interests = inviter.interests,
					)
				},
			)

		/** 받은 초대 목록을 응답 목록으로 변환한다. */
		fun listOf(invitations: List<ReceivedInvitation>): List<ReceivedInvitationResponse> =
			invitations.map { of(it) }
	}
}
