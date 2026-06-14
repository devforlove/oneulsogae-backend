package com.org.meeple.core.match.domain

import com.org.meeple.common.coin.CoinUsageType
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchType
import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.match.application.MatchErrorCode
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 남녀 1:1 매칭(소개) 도메인 모델.
 * 남녀 한 쌍을 식별하므로 (maleUserId, femaleUserId) 자체가 정규화된 쌍 키이며,
 * 영속성에서 이 쌍에 유니크 제약을 걸어 같은 쌍이 두 번 소개되는 것을 막는다. (재소개 방지)
 * [introducedDate]로 "하루에 한 번만 소개" 제약을 판단한다.
 * [expiresAt]까지 응답이 없으면 만료된 소개로 본다.
 * 양쪽의 수락/거절을 각각 보관하고, 둘 다 수락하면 성사([MatchStatus.MATCHED])된다.
 * [datingInitAmount]/[datingAcceptAmount]는 소개팅 신청/수락에 드는 코인 비용으로, [CoinUsageType]에서 가져와 세팅한다.
 * [matchType]은 이 소개가 생성된 경로(일일 배치/온보딩/필수 신청)를 나타낸다.
 * 영속성은 [com.org.meeple.infra.match.entity.MatchEntity]가 담당한다.
 */
data class Match(
	val id: Long = 0,
	val maleUserId: Long,
	val femaleUserId: Long,
	val introducedDate: LocalDate,
	val expiresAt: LocalDateTime,
	val matchType: MatchType,
	val maleAccepted: Boolean? = null,
	val femaleAccepted: Boolean? = null,
	val status: MatchStatus = MatchStatus.PROPOSED,
	val datingInitAmount: Int = CoinUsageType.DATING_INIT.coinAmount,
	val datingAcceptAmount: Int = CoinUsageType.DATING_ACCEPT.coinAmount,
) {

	/** 더 이상 응답을 받지 않는 종료 상태인지 여부. */
	val isClosed: Boolean
		get() = status.isClosed()

	/** 해당 사용자가 이 매칭의 참가자인지 여부. */
	fun isParticipant(userId: Long): Boolean =
		userId == maleUserId || userId == femaleUserId

	/** 주어진 참가자의 상대방 userId. (참가자가 아니면 호출하지 않는다) */
	fun partnerOf(userId: Long): Long =
		if (userId == maleUserId) femaleUserId else maleUserId

	/** 조회 사용자(userId 참가자)가 이 매칭에 관심(수락)을 보냈는지 여부. (아직 응답 전이면 false) */
	fun hasUserInterest(userId: Long): Boolean =
		(if (userId == maleUserId) maleAccepted else femaleAccepted) == true

	/** 상대방(userId의 반대편 참가자)이 이 매칭에 관심(수락)을 보냈는지 여부. (아직 응답 전이면 false) */
	fun hasPartnerInterest(userId: Long): Boolean =
		(if (userId == maleUserId) femaleAccepted else maleAccepted) == true

	/**
	 * 해당 사용자가 이 매칭에 응답/관심 보내기를 할 수 있는 상태인지 검증한다.
	 * 참가자가 아니면 [MatchErrorCode.NOT_MATCH_PARTICIPANT], 이미 종료된 매칭이면 [MatchErrorCode.MATCH_ALREADY_CLOSED]를 던진다.
	 */
	fun validateRespondable(userId: Long) {
		if (!isParticipant(userId)) {
			throw BusinessException(MatchErrorCode.NOT_MATCH_PARTICIPANT)
		}
		if (isClosed) {
			throw BusinessException(MatchErrorCode.MATCH_ALREADY_CLOSED)
		}
	}

	/**
	 * 참가자의 수락을 반영한 새 상태를 만든다. (참가자/미종료 검증은 호출 측 책임)
	 * 양쪽 모두 수락하면 MATCHED, 한쪽만 수락하고 상대 응답이 없으면 PARTIALLY_ACCEPTED, 양쪽 다 응답 전이면 PROPOSED 유지.
	 * 성사(MATCHED)되면 새 소개를 더 하지 않으므로, 만료로 목록에서 사라지지 않게 만료 시각을 100년 뒤로 미룬다.
	 */
	fun respond(userId: Long): Match {
		val responded: Match =
			if (userId == maleUserId) copy(maleAccepted = true) else copy(femaleAccepted = true)
		val recomputed: Match = responded.withRecomputedStatus()
		return if (recomputed.status == MatchStatus.MATCHED) recomputed.extendExpirationForMatched() else recomputed
	}

	private fun withRecomputedStatus(): Match =
		copy(
			status = when {
				maleAccepted == true && femaleAccepted == true -> MatchStatus.MATCHED
				maleAccepted == true || femaleAccepted == true -> MatchStatus.PARTIALLY_ACCEPTED
				else -> MatchStatus.PROPOSED
			},
		)

	// 성사된 매칭의 만료 시각을 [MATCHED_EXPIRATION_EXTENSION_YEARS]년 뒤로 미룬다. (성사 후엔 새 소개를 안 해 사실상 만료 없음)
	private fun extendExpirationForMatched(): Match =
		copy(expiresAt = expiresAt.plusYears(MATCHED_EXPIRATION_EXTENSION_YEARS))

	companion object {

		/** 소개(매칭)의 유효 기간. 생성 시각으로부터 이 기간이 지나면 만료된 것으로 본다. */
		val EXPIRATION: Duration = Duration.ofDays(1)

		/** 성사 매칭의 만료 연장 연수. 성사 후엔 새 소개를 안 해, 사실상 만료되지 않도록 만료 시각에 100년을 더한다. */
		const val MATCHED_EXPIRATION_EXTENSION_YEARS: Long = 100L

		/**
		 * 남녀 1:1 신규 소개를 생성한다. (status PROPOSED)
		 * 소개 일자(introducedDate)는 [now]의 날짜, 만료 시각(expiresAt)은 [now] + [EXPIRATION]으로 설정한다.
		 * 소개팅 신청/수락 코인 비용은 [CoinUsageType]에서 가져와 세팅하고, 소개 경로는 [matchType]으로 기록한다.
		 */
		fun propose(maleUserId: Long, femaleUserId: Long, matchType: MatchType, now: LocalDateTime): Match =
			Match(
				maleUserId = maleUserId,
				femaleUserId = femaleUserId,
				introducedDate = now.toLocalDate(),
				expiresAt = now.plus(EXPIRATION),
				matchType = matchType,
				datingInitAmount = CoinUsageType.DATING_INIT.coinAmount,
				datingAcceptAmount = CoinUsageType.DATING_ACCEPT.coinAmount,
			)

		/**
		 * 요청자의 성별을 기준으로 남/녀 자리를 배치해 신규 소개를 생성한다.
		 * 요청자가 남성이면 요청자가 maleUserId·상대가 femaleUserId가 되고, 여성이면 반대가 된다.
		 */
		fun propose(requesterId: Long, requesterGender: Gender, partnerId: Long, matchType: MatchType, now: LocalDateTime): Match =
			if (requesterGender == Gender.MALE) {
				propose(maleUserId = requesterId, femaleUserId = partnerId, matchType = matchType, now = now)
			} else {
				propose(maleUserId = partnerId, femaleUserId = requesterId, matchType = matchType, now = now)
			}
	}
}
