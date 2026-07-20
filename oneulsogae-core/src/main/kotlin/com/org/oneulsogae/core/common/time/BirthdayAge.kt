package com.org.oneulsogae.core.common.time

import java.time.LocalDate
import java.time.Period

/** 생년월일([this])과 기준일([today])로 만 나이를 계산한다. 나이 검증(도메인)과 표시(응답)에서 공통으로 쓴다. */
fun LocalDate.ageAt(today: LocalDate): Int = Period.between(this, today).years
