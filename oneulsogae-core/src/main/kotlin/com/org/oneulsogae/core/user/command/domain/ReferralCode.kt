package com.org.oneulsogae.core.user.command.domain

import java.util.Random

/**
 * 추천 코드 생성 규칙. `A-Z0-9` 8자 랜덤 문자열을 만든다.
 * 난수원은 파라미터로 주입받아 테스트에서 고정할 수 있다. (실사용은 SecureRandom)
 */
object ReferralCode {

	private const val LENGTH: Int = 8
	private const val CHARS: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

	fun generate(random: Random): String =
		buildString(LENGTH) {
			repeat(LENGTH) { append(CHARS[random.nextInt(CHARS.length)]) }
		}
}
