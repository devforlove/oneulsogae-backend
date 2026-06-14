package com.org.meeple.common.user

/** 음주 여부. */
enum class DrinkingStatus(val description: String) {

	NONE("전혀 마시지 않음"),
	SOMETIMES("가끔 마심"),
	OFTEN("자주 마심"),
}
