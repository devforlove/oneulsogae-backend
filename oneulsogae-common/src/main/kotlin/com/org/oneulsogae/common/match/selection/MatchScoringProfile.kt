package com.org.oneulsogae.common.match.selection

import com.org.oneulsogae.common.user.DrinkingStatus
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.Religion
import com.org.oneulsogae.common.user.SmokingStatus

/**
 * 이상형 우선순위 스코어링에 쓰는 read model. 유저의 실제 속성과 이상형(선호)을 함께 담는다.
 * user_details + user_ideal_types를 조인해 배치 시작 시 1회 적재한다. (양방향 부합 계산에 양쪽 데이터가 필요)
 * 나이는 조회 시점 today 기준으로 계산해 담는다(무연산 read model). null 속성/이상형은 "미상/상관없음".
 */
data class MatchScoringProfile(
	val userId: Long,
	val age: Int?,
	val height: Int?,
	val maritalStatus: MaritalStatus?,
	val smokingStatus: SmokingStatus?,
	val drinkingStatus: DrinkingStatus?,
	val religion: Religion?,
	val idealAgeMin: Int?,
	val idealAgeMax: Int?,
	val idealHeightMin: Int?,
	val idealHeightMax: Int?,
	val idealMaritalStatus: MaritalStatus?,
	val idealSmokingStatus: SmokingStatus?,
	val idealDrinkingStatus: DrinkingStatus?,
	val idealReligion: Religion?,
)
