package com.org.meeple.core.match.query.dto

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import java.time.LocalDate

/**
 * 내가 보낸 초대 현황(read model). 초대자(owner)가 자신이 보낸 초대 팀의 메타와 구성원 현황을 본다.
 * query 전용 view이며 command 도메인을 참조하지 않는다.
 */
data class SentInvitation(
	val teamId: Long,
	val name: String,
	val regionId: Long,
	/** 활동지역 표시 문자열(시/도 시/군/구). regions join으로 채운다. (지역 미설정/미존재면 null) */
	val activityArea: String?,
	val introduction: String?,
	val status: TeamStatus,
	val members: List<SentInvitationMember>,
)

/**
 * 초대 팀 구성원 한 명의 현황. status=ACTIVE는 초대자, INVITED는 수락 대기 중인 초대 대상.
 * 표시용 프로필(닉네임·직업·회사명·성별·프로필이미지)을 함께 담는다. (직업·회사명은 미입력 시 null)
 */
data class SentInvitationMember(
	val userId: Long,
	val nickname: String,
	val job: String?,
	val companyName: String?,
	val universityName: String?,
	val gender: Gender,
	val profileImageCode: String,
	val birthday: LocalDate,
	val status: TeamMemberStatus,
)
