package com.org.meeple.core.match.command.application

import com.org.meeple.common.match.MatchType
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.match.command.application.port.`in`.RecommendMatchUseCase
import com.org.meeple.core.match.command.application.port.out.GetMatchCandidatePort
import com.org.meeple.core.match.command.application.port.out.SaveMatchPort
import com.org.meeple.core.match.command.domain.Match
import com.org.meeple.core.match.command.domain.MatchableProfile
import com.org.meeple.core.user.query.dto.UserWithDetailView
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * [RecommendMatchUseCase] 구현.
 * 전달받은 사용자([UserWithDetail])가 매칭 가능한지 검증한 뒤, 반대 성별의 ACTIVE 사용자 1명을 소개한다.
 * 최근 [RECENT_LOGIN_WEEKS]주 이내에 로그인한 사용자만 후보로 고르며, 소개하는 즉시 [Match]를 PROPOSED로 저장해 이력에 남긴다.
 *
 * 소개할 후보가 없으면 null을 반환한다. 저장 실패(예: 동시 요청이 같은 쌍을 먼저 저장해 (maleUserId, femaleUserId)
 * 유니크 위반)는 흡수하지 않고 예외를 그대로 전파한다. 호출 측(온보딩 자동 소개 등)에서 실패를 인지하고 처리한다.
 */
@Service
class RecommendMatchService(
	private val getMatchCandidatePort: GetMatchCandidatePort,
	private val saveMatchPort: SaveMatchPort,
	private val timeGenerator: TimeGenerator,
) : RecommendMatchUseCase {

	override fun recommend(userWithDetail: UserWithDetailView): Match? {
		// 매칭 가능 여부(정식 가입 + 필수 필드)를 검증하고, 이후엔 non-null로 사용한다.
		val profile: MatchableProfile = MatchableProfile.from(userWithDetail)

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
			matchType = MatchType.ONBOARDING,
			now = now,
		)

		return saveMatchPort.save(match)
	}

	companion object {
		/** 매칭 후보로 인정하는 최근 로그인 기간(주). 이 기간 내 로그인한 사용자만 소개한다. */
		private const val RECENT_LOGIN_WEEKS = 2L
	}
}
