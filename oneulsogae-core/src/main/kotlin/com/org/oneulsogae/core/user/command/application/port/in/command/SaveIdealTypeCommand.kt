package com.org.oneulsogae.core.user.command.application.port.`in`.command

import com.org.oneulsogae.common.user.DrinkingStatus
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.Religion
import com.org.oneulsogae.common.user.SmokingStatus

/** 이상형 저장(upsert) 명령. 모든 항목은 선택이며 null = "상관없음". 나이/키는 min/max로 분해돼 전달된다. */
data class SaveIdealTypeCommand(
	val ageMin: Int?,
	val ageMax: Int?,
	val heightMin: Int?,
	val heightMax: Int?,
	val maritalStatus: MaritalStatus?,
	val smokingStatus: SmokingStatus?,
	val drinkingStatus: DrinkingStatus?,
	val religion: Religion?,
)
