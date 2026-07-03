package com.org.meeple.core.user.query.dto

import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus

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
