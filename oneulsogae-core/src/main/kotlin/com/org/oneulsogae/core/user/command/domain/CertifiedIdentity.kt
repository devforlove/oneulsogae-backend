package com.org.oneulsogae.core.user.command.domain

import com.org.oneulsogae.common.user.Gender
import java.time.LocalDate
import java.time.Period

/**
 * KCP 결과 조회·복호화로 얻은 검증된 신원 정보(값 객체).
 * ci/di는 웹 노출 금지 대상이며, 서비스는 도메인 판정 결과만 사용한다.
 */
data class CertifiedIdentity(
	val realName: String,
	val birthday: LocalDate,
	val gender: Gender,
	val phoneNumber: String,
	val ci: String,
	val di: String,
	val foreigner: Boolean,
	val telecom: String,
) {
	companion object {
		const val ADULT_AGE: Int = 19
	}

	fun age(today: LocalDate): Int = Period.between(birthday, today).years

	fun isAdult(today: LocalDate): Boolean = age(today) >= ADULT_AGE
}
