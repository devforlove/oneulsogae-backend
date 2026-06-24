package com.org.meeple.core.match.command.domain

import com.org.meeple.common.coin.CoinUsageType
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.TeamMatchType
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

	/** 성사(MATCHED)된 매칭인지 여부. */
	fun isMatched(): Boolean =
		status == MatchStatus.MATCHED

	companion object {

		/** 팀 매칭의 유효 기간. 생성 시각으로부터 이 기간이 지나면 만료된 것으로 본다. */
		val EXPIRATION: Duration = Duration.ofDays(1)

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
