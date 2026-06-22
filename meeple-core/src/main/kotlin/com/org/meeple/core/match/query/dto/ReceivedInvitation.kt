package com.org.meeple.core.match.query.dto

import com.org.meeple.common.user.Gender
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 내가 받은 초대 한 건(read model). 초대받은(INVITED) 유저가 보는, 대기 중(INVITING) 팀과 그 팀의 ACTIVE 구성원 프로필.
 * query 전용 view이며 command 도메인을 참조하지 않는다.
 * [invitedAt]은 내가 초대된 시각(team_members(me).created_at)으로, 화면 캡션의 "언제 초대됐는지" 표시에 쓴다.
 * [participants]는 이 팀에 이미 합류해 있는 ACTIVE 구성원 목록(생성 순)으로, 화면의 "함께 나온 친구"에 그대로 대응한다.
 * (2:2에서는 1명이지만, 팀이 커지면 여러 명일 수 있어 리스트로 담는다)
 */
data class ReceivedInvitation(
	val teamId: Long,
	val name: String,
	val introduction: String?,
	val invitedAt: LocalDateTime,
	val participants: List<ReceivedInvitationParticipant>,
)

/**
 * 초대 팀의 ACTIVE 구성원(=초대한 상대방) 프로필. 닉네임·프로필이미지·나이는 match_user, 그 외 상세(직업·회사명·키·지역·자기소개·특성·관심사)는 user_details에서 온다.
 * 키·지역·자기소개·특성·관심사는 카드 상세 시트에서 쓰는 값으로, 비공개면 null/빈 배열일 수 있다.
 */
data class ReceivedInvitationParticipant(
	val userId: Long,
	val nickname: String,
	val job: String?,
	val companyName: String?,
	val gender: Gender,
	val profileImageCode: String,
	val birthday: LocalDate,
	val height: Int?,
	val activityArea: String?,
	val introduction: String?,
	val traits: List<String>,
	val interests: List<String>,
)
