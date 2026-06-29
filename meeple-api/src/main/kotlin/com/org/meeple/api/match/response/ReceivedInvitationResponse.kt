package com.org.meeple.api.match.response

import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.time.ageAt
import com.org.meeple.core.match.query.dto.ReceivedInvitation
import com.org.meeple.core.match.query.dto.ReceivedInvitationParticipant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 내가 받은 초대 한 건 응답. 팀 메타와 내가 초대된 시각, 그 팀의 ACTIVE 구성원(=초대한 상대방) 목록을 담는다.
 * [participants]는 생성 순으로, 2:2에서는 1명이지만 팀이 커지면 여러 명일 수 있어 리스트다.
 */
data class ReceivedInvitationResponse(
	val teamId: Long,
	val name: String,
	val introduction: String?,
	val invitedAt: LocalDateTime,
	val participants: List<Participant>,
) {

	/**
	 * 초대 팀의 ACTIVE 구성원 프로필. 닉네임·직업·회사명·성별·프로필이미지·나이에 더해
	 * 카드 상세 시트용 키·지역·자기소개·특성·관심사를 담는다. (비공개면 null/빈 배열)
	 */
	data class Participant(
		val userId: Long,
		val nickname: String,
		val job: String?,
		val companyName: String?,
		val universityName: String?,
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
		fun of(invitation: ReceivedInvitation, today: LocalDate): ReceivedInvitationResponse =
			ReceivedInvitationResponse(
				teamId = invitation.teamId,
				name = invitation.name,
				introduction = invitation.introduction,
				invitedAt = invitation.invitedAt,
				participants = invitation.participants.map { participant: ReceivedInvitationParticipant ->
					Participant(
						userId = participant.userId,
						nickname = participant.nickname,
						job = participant.job,
						companyName = participant.companyName,
						universityName = participant.universityName,
						gender = participant.gender,
						profileImageCode = participant.profileImageCode,
						age = participant.birthday.ageAt(today),
						height = participant.height,
						activityArea = participant.activityArea,
						introduction = participant.introduction,
						traits = participant.traits,
						interests = participant.interests,
					)
				},
			)

		/** 받은 초대 목록을 응답 목록으로 변환한다. */
		fun listOf(invitations: List<ReceivedInvitation>, today: LocalDate): List<ReceivedInvitationResponse> =
			invitations.map { of(it, today) }
	}
}
