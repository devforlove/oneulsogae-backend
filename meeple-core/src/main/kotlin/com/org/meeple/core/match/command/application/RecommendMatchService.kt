package com.org.meeple.core.match.command.application

import com.org.meeple.common.match.SoloMatchType
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.match.command.application.port.`in`.RecommendMatchUseCase
import com.org.meeple.core.match.command.application.port.out.GetMatchCandidatePort
import com.org.meeple.core.match.command.application.port.out.GetMatchUserPort
import com.org.meeple.core.match.command.application.port.out.SaveMatchPort
import com.org.meeple.core.match.command.domain.Match
import com.org.meeple.core.match.command.domain.MatchUser
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * [RecommendMatchUseCase] 구현.
 * 요청자([userId])의 매칭 읽기 모델([MatchUser])을 읽어, 반대 성별·같은 활동 권역의 사용자 1명을 소개한다.
 * 최근 [RECENT_LOGIN_WEEKS]주 이내에 로그인한 사용자만 후보로 고르며, 소개하는 즉시 [Match]를 PROPOSED로 저장해 이력에 남긴다.
 *
 * 요청자가 아직 매칭 가능 상태가 아니거나(읽기 모델 미적재) 소개할 후보가 없으면 null을 반환한다. (둘 다 이번엔 소개 생략)
 * 저장 실패(예: 동시 요청이 같은 쌍을 먼저 저장해 유니크 위반)는 흡수하지 않고 예외를 그대로 전파한다.
 */
@Service
class RecommendMatchService(
	private val getMatchUserPort: GetMatchUserPort,
	private val getMatchCandidatePort: GetMatchCandidatePort,
	private val saveMatchPort: SaveMatchPort,
	private val timeGenerator: TimeGenerator,
) : RecommendMatchUseCase {

	override fun recommend(userId: Long): Match? {
		// 매칭 읽기 모델에 적재돼 있어야 매칭 가능(정식 가입 + 필수 필드 완성)이다. 없으면 이번엔 소개를 생략한다.
		val profile: MatchUser = getMatchUserPort.findByUserId(userId) ?: return null

		// 최근 RECENT_LOGIN_WEEKS주 이내 로그인한 사용자만 후보로 삼는다. 후보가 없으면 null(이번엔 소개 생략).
		val loginAfter: LocalDateTime = timeGenerator.now().minusWeeks(RECENT_LOGIN_WEEKS)
		// 같은 활동 권역(regionCode)의 반대 성별 후보만 소개한다.
		val candidateId: Long = getMatchCandidatePort.findOneCandidate(
			profile.partnerGender(),
			profile.regionCode,
			loginAfter,
		)
			?: return null

		// 소개 일자/만료 시각의 기준이 되는 현재 시각. (만료 = now + Match.EXPIRATION)
		val now: LocalDateTime = timeGenerator.now()
		val match: Match = Match.propose(
			requesterId = profile.userId,
			requesterGender = profile.gender,
			partnerId = candidateId,
			matchType = SoloMatchType.ONBOARDING,
			now = now,
		)

		return saveMatchPort.save(match)
	}

	companion object {
		/** 매칭 후보로 인정하는 최근 로그인 기간(주). 이 기간 내 로그인한 사용자만 소개한다. */
		private const val RECENT_LOGIN_WEEKS = 2L
	}
}
