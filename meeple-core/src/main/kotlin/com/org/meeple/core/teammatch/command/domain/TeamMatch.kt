package com.org.meeple.core.teammatch.command.domain

import com.org.meeple.common.coin.CoinUsageType
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.TeamMatchType
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.teammatch.TeamMatchErrorCode
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 2:2(팀) 매칭 애그리거트의 도메인 모델. 독립적으로 결성된 두 팀을 소개로 묶는다.
 * 참가 팀(과 매치별 수락 여부)은 [matchedTeams]가 보관한다.
 * 두 팀 조합의 정규화 키([memberKey])로 같은 조합의 중복 소개를 차단한다. (재소개 방지)
 */
data class TeamMatch(
	val id: Long = 0,
	/** 낙관적 락 버전. 영속성에서 읽어 저장까지 실어 보내, 애그리거트 동시 변경을 감지하는 데 쓴다. (DB가 증가시킨다) */
	val version: Long = 0,
	val matchedTeams: MatchedTeams,
	val introducedDate: LocalDate,
	val expiresAt: LocalDateTime,
	val matchType: TeamMatchType,
	val status: MatchStatus = MatchStatus.PROPOSED,
	val dateInitAmount: Int = CoinUsageType.MEETING_INIT.coinAmount,
	val dateAcceptAmount: Int = CoinUsageType.MEETING_ACCEPT.coinAmount,
	val deletedAt: LocalDateTime? = null,
) {

	/** 참가 팀 조합을 식별하는 정규화 키. (재소개 방지 유니크 키) */
	fun memberKey(): String =
		matchedTeams.memberKey()

	/** 참가 팀들에 이 팀 매칭의 id([teamMatchId])를 채워 반환한다. (영속화 직전, 헤더 저장으로 id를 얻은 뒤 호출) */
	fun matchedTeamsWith(teamMatchId: Long): MatchedTeams =
		matchedTeams.withTeamMatchId(teamMatchId)

	/** 미성사 매칭을 종료한 새 모델. status를 CLOSED로 바꾸고 참가 팀 전원을 DEACTIVE로 전이한다. (기록은 보존, 소프트 삭제 안 함) */
	fun close(): TeamMatch =
		copy(status = MatchStatus.CLOSED, matchedTeams = matchedTeams.deactivateAll())

	/** 더 이상 응답을 받지 않는 종료(성사 MATCHED 포함) 상태인지 여부. */
	val isClosed: Boolean
		get() = status.isClosed()

	/** 성사(MATCHED)된 매칭인지 여부. */
	fun isMatched(): Boolean =
		status == MatchStatus.MATCHED

	/** [teamId]가 이 팀 매칭의 참가 팀인지 여부. */
	fun isParticipant(teamId: Long): Boolean =
		matchedTeams.isParticipant(teamId)

	/** 참가 팀 id 목록. (참가 두 팀을 로드할 때) */
	fun teamIds(): List<Long> =
		matchedTeams.teamIds()

	/**
	 * 해당 팀이 이 팀 매칭에 관심(신청)을 보낼 수 있는 상태인지 검증한다.
	 * 참가 팀이 아니면 [TeamMatchErrorCode.NOT_TEAM_MATCH_PARTICIPANT], 이미 종료된 매칭이면 [TeamMatchErrorCode.TEAM_MATCH_ALREADY_CLOSED]를 던진다.
	 */
	fun validateRespondable(teamId: Long) {
		if (!isParticipant(teamId)) {
			throw BusinessException(TeamMatchErrorCode.NOT_TEAM_MATCH_PARTICIPANT)
		}
		if (status.isClosed()) {
			throw BusinessException(TeamMatchErrorCode.TEAM_MATCH_ALREADY_CLOSED)
		}
	}

	/**
	 * [teamId] 팀이 이 팀 매칭을 종료할 수 있는 상태인지 검증한다.
	 * 참가 팀이 아니면 [TeamMatchErrorCode.NOT_TEAM_MATCH_PARTICIPANT], 이미 종료(CLOSED)면 [TeamMatchErrorCode.TEAM_MATCH_ALREADY_CLOSED],
	 * 아직 성사(MATCHED)되지 않았으면 [TeamMatchErrorCode.TEAM_MATCH_NOT_MATCHED]를 던진다. (성사된 매칭만 종료 가능)
	 */
	fun validateTerminable(teamId: Long) {
		if (!isParticipant(teamId)) {
			throw BusinessException(TeamMatchErrorCode.NOT_TEAM_MATCH_PARTICIPANT)
		}
		// MATCHED도 isClosed()=true(더 이상 응답을 안 받음)라, 여기선 종료(CLOSED)만 따로 거른다.
		if (status == MatchStatus.CLOSED) {
			throw BusinessException(TeamMatchErrorCode.TEAM_MATCH_ALREADY_CLOSED)
		}
		if (status != MatchStatus.MATCHED) {
			throw BusinessException(TeamMatchErrorCode.TEAM_MATCH_NOT_MATCHED)
		}
		// 헤더가 MATCHED로 남아 있어도, 이미 나간(비활성) 팀이 다시 종료를 호출하면 막는다. (중복 나감 처리 방지)
		if (matchedTeams.find(teamId)?.isDeactivated == true) {
			throw BusinessException(TeamMatchErrorCode.TEAM_MATCH_ALREADY_CLOSED)
		}
	}

	/** [teamId]의 상대 팀이 모두 비활성인지 여부. (이 팀이 나가면 방에 남아 알림을 받을 상대 팀이 없는 마지막 종료) */
	fun isLastActiveTeam(teamId: Long): Boolean =
		matchedTeams.isLastActiveTeam(teamId)

	/**
	 * 이 매칭(헤더+참가 팀)을 [now]에 종료([MatchStatus.CLOSED])하고 소프트 삭제(제거)한 새 모델을 반환한다.
	 * 마지막 팀이 나가 남는 팀이 없을 때 호출한다. 저장하면 status가 CLOSED가 되고 deletedAt이 채워져 이후 조회에서 제외된다.
	 */
	fun delete(now: LocalDateTime): TeamMatch =
		copy(status = MatchStatus.CLOSED, deletedAt = now, matchedTeams = matchedTeams.delete(now))

	/**
	 * [teamId] 팀이 이 매칭을 나간 새 모델을 반환한다.
	 * 본인 팀 참가([MatchedTeam])만 비활성([MatchedTeamStatus.DEACTIVE])으로 전이하되, 상대 팀도 모두 비활성이면(마지막 활성 팀이 나가면)
	 * 매칭 헤더까지 종료([MatchStatus.CLOSED])·소프트 삭제([delete])한다. (혼자 나가면 헤더는 MATCHED로 유지되고 상대 팀은 그대로 남는다)
	 */
	fun leave(teamId: Long, now: LocalDateTime): TeamMatch {
		val left: TeamMatch = copy(matchedTeams = matchedTeams.deactivate(teamId))
		return if (left.matchedTeams.allDeactivated()) left.delete(now) else left
	}

	/**
	 * 참가 팀의 관심 신청을 반영한 새 상태를 만든다. (참가/미종료 검증은 호출 측 책임)
	 * 응답 팀을 APPLY로 바꾸고 지불자([applicantUserId])를 기록한다. 전원 신청이면 MATCHED로 만들며 전원을 ACTIVE로 승격한다.
	 * 일부만 신청이면 PARTIALLY_ACCEPTED. 성사(MATCHED)되면 만료로 목록에서 사라지지 않게 만료 시각을 100년 뒤로 미룬다.
	 */
	fun respond(teamId: Long, applicantUserId: Long): TeamMatch {
		val applied: TeamMatch = copy(matchedTeams = matchedTeams.apply(teamId, applicantUserId))
		val recomputed: TeamMatch = applied.withRecomputedStatus()
		return if (recomputed.status == MatchStatus.MATCHED) recomputed.extendExpirationForMatched() else recomputed
	}

	/**
	 * 미성사(만료) 제거 시, 신청한 팀의 지불자별 환불 금액 목록을 산정한다. (1:1 [Match.failureRefunds] 미러)
	 * 신청(APPLY)했으나 성사되지 못한 팀의 [MatchedTeam.applicantUserId]에게만 신청 비용([dateInitAmount])의 절반(내림)을 돌려준다. (성사로 ACTIVE가 된 팀은 제외)
	 */
	fun failureRefunds(): List<MatchRefund> =
		matchedTeams.refundableTeams()
			.mapNotNull { team: MatchedTeam -> team.applicantUserId?.let { userId: Long -> MatchRefund(userId = userId, amount = dateInitAmount / 2) } }
			.filter { refund: MatchRefund -> refund.amount > 0 }

	private fun withRecomputedStatus(): TeamMatch =
		when {
			matchedTeams.allApplied() -> copy(status = MatchStatus.MATCHED, matchedTeams = matchedTeams.activateAll())
			matchedTeams.anyApplied() -> copy(status = MatchStatus.PARTIALLY_ACCEPTED)
			else -> copy(status = MatchStatus.PROPOSED)
		}

	// 성사된 매칭의 만료 시각을 [MATCHED_EXPIRATION_EXTENSION_YEARS]년 뒤로 미룬다. (성사 후엔 새 소개를 안 해 사실상 만료 없음)
	private fun extendExpirationForMatched(): TeamMatch =
		copy(expiresAt = expiresAt.plusYears(MATCHED_EXPIRATION_EXTENSION_YEARS))

	companion object {

		/** 팀 매칭의 유효 기간. 생성 시각으로부터 이 기간이 지나면 만료된 것으로 본다. */
		val EXPIRATION: Duration = Duration.ofDays(1)

		/** 성사 매칭의 만료 연장 연수. 성사 후엔 새 소개를 안 해, 사실상 만료되지 않도록 100년을 더한다. (1:1 [Match]와 동일) */
		const val MATCHED_EXPIRATION_EXTENSION_YEARS: Long = 100L

		/**
		 * 두 팀([teamAId], [teamBId])을 참가 팀으로 하는 신규 팀 매칭을 생성한다. (status PROPOSED, 양쪽 팀 WAITING)
		 * 소개 일자(introducedDate)는 [now]의 날짜, 만료 시각(expiresAt)은 [now] + [EXPIRATION]으로 설정한다.
		 * 팀 매칭 신청/수락 코인 비용은 [CoinUsageType]에서 가져오고, 생성 경로는 [matchType]으로 기록한다.
		 */
		fun propose(teamAId: Long, teamBId: Long, matchType: TeamMatchType, now: LocalDateTime): TeamMatch =
			TeamMatch(
				matchedTeams = MatchedTeams.of(listOf(teamAId, teamBId)),
				introducedDate = now.toLocalDate(),
				expiresAt = now.plus(EXPIRATION),
				matchType = matchType,
			)
	}
}
