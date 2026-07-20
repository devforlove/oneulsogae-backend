package com.org.oneulsogae.core.user.query.dto

import com.org.oneulsogae.common.user.DrinkingStatus
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.Religion
import com.org.oneulsogae.common.user.SmokingStatus

/** 이상형 조회 read model. null = "상관없음". */
data class IdealTypeView(
	val ageMin: Int?,
	val ageMax: Int?,
	val heightMin: Int?,
	val heightMax: Int?,
	val maritalStatus: MaritalStatus?,
	val smokingStatus: SmokingStatus?,
	val drinkingStatus: DrinkingStatus?,
	val religion: Religion?,
)
