package com.org.meeple.core.match.query.dto

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.user.BodyType
import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 매칭 목록 조회 결과(read model). 조회 사용자 기준으로 매칭 상태와 상대방 프로필을 한 단계(평탄)로 담는다.
 * query는 command 도메인([com.org.meeple.core.match.command.domain.Match])·user 도메인(UserDetail)에 의존하지 않고
 * 이 평탄 read model을 쓴다. 관심 여부는 dao가 참가자 수락 플래그에서 직접 산출해 채운다.
 */
data class MatchWithPartner(
	val matchId: Long,
	val status: MatchStatus,
	val expiresAt: LocalDateTime,
	val datingInitAmount: Int,
	val datingAcceptAmount: Int,
	/** 조회 사용자가 이 매칭에 관심을 보냈는지 여부. */
	val hasUserInterest: Boolean,
	/** 상대방이 이 매칭에 관심을 보냈는지 여부. */
	val hasPartnerInterest: Boolean,
	val partnerUserId: Long,
	val nickname: String?,
	val profileImageCode: String?,
	val birthday: LocalDate?,
	val height: Int?,
	val gender: Gender?,
	val job: String?,
	val activityArea: String?,
	val introduction: String?,
	val companyName: String?,
	val universityName: String?,
	val traits: List<String>,
	val interests: List<String>,
	val maritalStatus: MaritalStatus?,
	val smokingStatus: SmokingStatus?,
	val religion: Religion?,
	val drinkingStatus: DrinkingStatus?,
	val bodyType: BodyType?,
)
