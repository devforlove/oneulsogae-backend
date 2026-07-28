package com.org.oneulsogae.core.user.command.domain

import java.security.MessageDigest
import java.time.LocalDateTime

/**
 * 추천 보상 지급 이력. **한 사람에게 평생 1회만** 추천 보상이 나가도록 막는 어뷰징 방어 기록이다.
 *
 * 판정 키는 본인확인(KCP)의 중복가입확인정보(DI)다. DI는 같은 사람이면 탈퇴·재가입해도 같은 값이라,
 * "탈퇴 → 유예 경과 → 재가입 → 추천 보상 재수령" 경로를 막을 수 있는 유일한 식별자다.
 * (전화번호 중복 검사는 동시에 존재하는 계정끼리만 막고, 파기 배치가 phone_number·di를 지우면 무력해진다)
 *
 * 원본 DI는 보관하지 않고 [hashDi]로 만든 해시만 남긴다. 파기(익명화) 대상이 아니며, 이 기록이 지워지면
 * 방어가 풀리므로 소프트 삭제도 하지 않는다.
 */
data class ReferralRewardGrant(
	val id: Long = 0,
	/** 피추천인 DI의 해시. 지급 여부 판정 키. */
	val referredDiHash: String,
	val referrerUserId: Long,
	val referredUserId: Long,
	val coinAmount: Int,
	val grantedAt: LocalDateTime,
) {
	companion object {

		/**
		 * DI를 `salt + di`의 SHA-256 hex로 해싱한다. 원본 DI를 저장하지 않으면서 동일인 재가입을 판정하기 위한 것이다.
		 * [salt]는 운영 설정값이며, 바꾸면 기존 이력과 매칭되지 않아 방어가 초기화된다. (고정 유지)
		 */
		fun hashDi(di: String, salt: String): String =
			MessageDigest.getInstance("SHA-256")
				.digest((salt + di).toByteArray())
				.joinToString("") { byte: Byte -> "%02x".format(byte) }

		fun create(referredDiHash: String, referrerUserId: Long, referredUserId: Long, coinAmount: Int, grantedAt: LocalDateTime): ReferralRewardGrant =
			ReferralRewardGrant(
				referredDiHash = referredDiHash,
				referrerUserId = referrerUserId,
				referredUserId = referredUserId,
				coinAmount = coinAmount,
				grantedAt = grantedAt,
			)
	}
}
