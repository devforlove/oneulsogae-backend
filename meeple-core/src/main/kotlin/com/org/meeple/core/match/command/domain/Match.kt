package com.org.meeple.core.match.command.domain

import com.org.meeple.common.coin.CoinUsageType
import com.org.meeple.common.match.MatchMemberStatus
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.SoloMatchType
import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.match.MatchErrorCode
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 매칭(소개) 도메인 모델.
 * 참가자는 더 이상 (male, female) 두 자리로 고정하지 않고, 참가자([MatchMembers])로 정규화해 1:1·N:N(2:2·3:3)을 모두 표현한다.
 * 재소개 방지는 참가자 조합의 정규화 키([memberKey])에 유니크 제약을 걸어 막는다.
 * [introducedDate]로 "하루에 한 번만 소개" 제약을 판단하고, [expiresAt]까지 응답이 없으면 만료된 소개로 본다.
 * 각 참가자의 상태(WAITING→APPLY→ACTIVE/DEACTIVE)는 [members]가 보관하며, 전원 신청(APPLY)하면 성사([MatchStatus.MATCHED])되어 전원 ACTIVE가 된다.
 * [datingInitAmount]/[datingAcceptAmount]는 소개팅 신청/수락 코인 비용([CoinUsageType])이고, [matchType]은 생성 경로(일일 배치/온보딩/필수 신청)다.
 * 영속성은 [com.org.meeple.infra.match.command.entity.SoloMatchEntity](헤더) + [com.org.meeple.infra.match.command.entity.SoloMatchMemberEntity](참가자)가 담당한다.
 */
data class Match(
	val id: Long = 0,
	val members: MatchMembers,
	val introducedDate: LocalDate,
	val expiresAt: LocalDateTime,
	val matchType: SoloMatchType,
	val status: MatchStatus = MatchStatus.PROPOSED,
	val datingInitAmount: Int = CoinUsageType.DATING_INIT.coinAmount,
	val datingAcceptAmount: Int = CoinUsageType.DATING_ACCEPT.coinAmount,
	val deletedAt: LocalDateTime? = null,
) {

	/** 더 이상 응답을 받지 않는 종료 상태인지 여부. */
	val isClosed: Boolean
		get() = status.isClosed()

	/**
	 * 이 매칭(헤더+참가자)을 [now]에 종료([MatchStatus.CLOSED])하고 소프트 삭제(제거)한 새 모델을 반환한다.
	 * 채팅방 나가기 등으로 관계가 끝났을 때 호출한다. 저장하면 상태가 CLOSED가 되고 deletedAt이 채워져 이후 조회에서 제외된다.
	 */
	fun delete(now: LocalDateTime): Match =
		copy(status = MatchStatus.CLOSED, deletedAt = now, members = members.delete(now))

	/**
	 * [userId] 참가자를 비활성([MatchMemberStatus.DEACTIVE])으로 전이한 새 모델을 반환한다.
	 * 한 참가자가 채팅방을 나갔지만 방은 닫히지 않을 때(매칭은 유지) 그 참가자의 매칭 참가만 비활성화한다.
	 */
	fun deactivateMember(userId: Long): Match =
		copy(members = members.deactivate(userId))

	/**
	 * [userId] 참가자가 이 매칭을 나간 새 모델을 반환한다.
	 * 본인 참가만 비활성([MatchMemberStatus.DEACTIVE])으로 전이하되, 그 결과 상대 참가자도 모두 비활성이면(마지막 활성 참가자가 나가면)
	 * 매칭 헤더까지 종료([MatchStatus.CLOSED])·소프트 삭제([delete])한다. (혼자 나가면 매칭은 유지되고 상대는 그대로 남는다)
	 */
	fun leave(userId: Long, now: LocalDateTime): Match {
		val left: Match = deactivateMember(userId)
		return if (left.members.partnersOf(userId).all { it.isDeactivated }) left.delete(now) else left
	}

	/** 해당 사용자가 이 매칭의 참가자인지 여부. */
	fun isParticipant(userId: Long): Boolean =
		members.isParticipant(userId)

	/** 주어진 참가자의 상대 userId. (1:1 전제, 참가자가 아니면 호출하지 않는다) */
	fun partnerOf(userId: Long): Long =
		members.partnersOf(userId).first().userId

	/** 참가자 userId 전체. (채팅방 생성 등 참가자 목록이 필요할 때) */
	fun participantUserIds(): List<Long> =
		members.userIds()

	/** 참가자들에 이 매칭의 id([matchId])를 채워 반환한다. (영속화 직전, 헤더 저장으로 id를 얻은 뒤 호출) */
	fun membersWith(matchId: Long): MatchMembers =
		members.withMatchId(matchId)

	/**
	 * 매칭 실패(미성사 만료/채팅 종료)로 제거될 때, 참가자별 환불 금액 목록을 산정한다.
	 * 실제로 코인을 지불한(신청한) 참가자에게만, 신청 비용([datingInitAmount])의 절반(내림)을 돌려준다.
	 * (소개팅 신청·수락 비용이 동일하다는 전제이며, 0코인 환불은 제외한다)
	 */
	fun failureRefunds(): List<MatchRefund> =
		members.applied()
			.map { member: MatchMember -> MatchRefund(userId = member.userId, amount = datingInitAmount / 2) }
			.filter { refund: MatchRefund -> refund.amount > 0 }

	/** 참가자 조합을 식별하는 정규화 키. (재소개 방지 유니크 키) */
	fun memberKey(): String =
		members.memberKey()

	/** 조회 사용자가 이 매칭에 관심(신청)을 보냈는지 여부. (미신청이면 false) */
	fun hasUserInterest(userId: Long): Boolean =
		members.find(userId)?.hasApplied == true

	/** 상대(조회 사용자의 반대편 참가자)가 이 매칭에 관심(신청)을 보냈는지 여부. (미신청이면 false) */
	fun hasPartnerInterest(userId: Long): Boolean =
		members.partnersOf(userId).any { it.hasApplied }

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
	 * 해당 사용자가 이 매칭을 종료할 수 있는 상태인지 검증한다.
	 * 참가자가 아니면 [MatchErrorCode.NOT_MATCH_PARTICIPANT], 이미 종료된 매칭이거나 본인이 이미 나간(비활성) 참가자면 [MatchErrorCode.MATCH_ALREADY_CLOSED],
	 * 아직 성사되지 않은(MATCHED가 아닌) 매칭이면 [MatchErrorCode.MATCH_NOT_MATCHED]를 던진다. (성사된 매칭만 종료 가능)
	 */
	fun validateTerminable(userId: Long) {
		if (!isParticipant(userId)) {
			throw BusinessException(MatchErrorCode.NOT_MATCH_PARTICIPANT)
		}
		// MATCHED도 isClosed()=true(더 이상 응답을 안 받음)라, 여기선 종료(CLOSED)만 따로 거른다.
		if (status == MatchStatus.CLOSED) {
			throw BusinessException(MatchErrorCode.MATCH_ALREADY_CLOSED)
		}
		if (status != MatchStatus.MATCHED) {
			throw BusinessException(MatchErrorCode.MATCH_NOT_MATCHED)
		}
		// 헤더가 MATCHED로 남아 있어도, 이미 나간(비활성) 참가자가 다시 종료를 호출하면 막는다. (중복 나감 안내·재처리 방지)
		if (members.find(userId)?.isDeactivated == true) {
			throw BusinessException(MatchErrorCode.MATCH_ALREADY_CLOSED)
		}
	}

	/**
	 * 참가자의 관심 신청을 반영한 새 상태를 만든다. (참가자/미종료 검증은 호출 측 책임)
	 * 응답자를 APPLY로 바꾸고, 전원 신청이면 매치를 MATCHED로 만들며 전원을 ACTIVE로 승격한다. 일부만 신청이면 PARTIALLY_ACCEPTED, 아무도 미신청이면 PROPOSED.
	 * 성사(MATCHED)되면 만료로 목록에서 사라지지 않게 만료 시각을 100년 뒤로 미룬다.
	 */
	fun respond(userId: Long): Match {
		val applied: Match = copy(members = members.apply(userId))
		val recomputed: Match = applied.withRecomputedStatus()
		return if (recomputed.status == MatchStatus.MATCHED) recomputed.extendExpirationForMatched() else recomputed
	}

	private fun withRecomputedStatus(): Match =
		when {
			members.allApplied() -> copy(status = MatchStatus.MATCHED, members = members.activateAll())
			members.anyApplied() -> copy(status = MatchStatus.PARTIALLY_ACCEPTED)
			else -> copy(status = MatchStatus.PROPOSED)
		}

	// 성사된 매칭의 만료 시각을 [MATCHED_EXPIRATION_EXTENSION_YEARS]년 뒤로 미룬다. (성사 후엔 새 소개를 안 해 사실상 만료 없음)
	private fun extendExpirationForMatched(): Match =
		copy(expiresAt = expiresAt.plusYears(MATCHED_EXPIRATION_EXTENSION_YEARS))

	companion object {

		/** 소개(매칭)의 유효 기간. 생성 시각으로부터 이 기간이 지나면 만료된 것으로 본다. */
		val EXPIRATION: Duration = Duration.ofDays(1)

		/** 성사 매칭의 만료 연장 연수. 성사 후엔 새 소개를 안 해, 사실상 만료되지 않도록 만료 시각에 100년을 더한다. */
		const val MATCHED_EXPIRATION_EXTENSION_YEARS: Long = 100L

		/**
		 * 요청자와 상대를 참가자로 하는 신규 소개를 생성한다. (status PROPOSED)
		 * 참가자는 요청자([requesterId], [requesterGender])와 상대([partnerId], 반대 성별)로 구성한다.
		 * 소개 일자(introducedDate)는 [now]의 날짜, 만료 시각(expiresAt)은 [now] + [EXPIRATION]으로 설정한다.
		 * 소개팅 신청/수락 코인 비용은 [CoinUsageType]에서 가져오고, 소개 경로는 [matchType]으로 기록한다.
		 */
		fun propose(requesterId: Long, requesterGender: Gender, partnerId: Long, matchType: SoloMatchType, now: LocalDateTime): Match =
			Match(
				members = MatchMembers.of(
					listOf(
						requesterId to requesterGender,
						partnerId to requesterGender.opposite(),
					),
				),
				introducedDate = now.toLocalDate(),
				expiresAt = now.plus(EXPIRATION),
				matchType = matchType,
				datingInitAmount = CoinUsageType.DATING_INIT.coinAmount,
				datingAcceptAmount = CoinUsageType.DATING_ACCEPT.coinAmount,
			)
	}
}
