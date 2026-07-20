package com.org.oneulsogae.core.solomatch.command.application

import com.org.oneulsogae.common.coin.CoinUsageType
import com.org.oneulsogae.common.match.SoloMatchType
import com.org.oneulsogae.core.coin.command.application.port.`in`.SpendCoinUseCase
import com.org.oneulsogae.core.coin.command.application.port.`in`.command.SpendCoinCommand
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.lock.DistributedLock
import com.org.oneulsogae.core.common.lock.LockKeyConstraints
import com.org.oneulsogae.core.common.region.GetRegionProximityPort
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.matchuser.command.application.port.`in`.GetMatchUserUseCase
import com.org.oneulsogae.core.matchuser.command.domain.MatchUser
import com.org.oneulsogae.core.solomatch.MatchErrorCode
import com.org.oneulsogae.core.solomatch.command.application.port.`in`.IntroduceExtraMatchUseCase
import com.org.oneulsogae.core.solomatch.command.application.port.out.ExtraIntroCandidateRow
import com.org.oneulsogae.core.solomatch.command.application.port.out.GetExtraIntroCandidatePort
import com.org.oneulsogae.core.solomatch.command.application.port.out.SaveMatchPort
import com.org.oneulsogae.core.solomatch.command.domain.Match
import com.org.oneulsogae.common.match.selection.MatchScoringProfile
import com.org.oneulsogae.common.match.selection.MatchSelector
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * [IntroduceExtraMatchUseCase] 구현. 요청자의 자격 후보를 이상형·거리·최근 종합 점수([MatchSelector])로
 * 정렬해 재소개 이력 없는 최고점 후보 1명을 고른다. 후보가 있으면 추가 소개 코인([CoinUsageType.EXTRA_INTRO])을
 * 차감하고 [SoloMatchType.EXTRA] PROPOSED 매칭을 만든다. 후보가 없으면 코인을 차감하지 않고 예외.
 *
 * 코인 차감·매칭 저장은 같은 트랜잭션이라 저장 실패(유니크 위반 등) 시 차감도 롤백된다.
 * 요청자별 분산 락([LockKeyConstraints.EXTRA_INTRO])으로 더블클릭 이중 과금을 fail-fast(waitTime=0)로 막는다.
 */
@Service
class IntroduceExtraMatchService(
	private val getMatchUserUseCase: GetMatchUserUseCase,
	private val getExtraIntroCandidatePort: GetExtraIntroCandidatePort,
	private val getRegionProximityPort: GetRegionProximityPort,
	private val spendCoinUseCase: SpendCoinUseCase,
	private val saveMatchPort: SaveMatchPort,
	private val timeGenerator: TimeGenerator,
	private val random: Random = Random.Default,
) : IntroduceExtraMatchUseCase {

	@DistributedLock(prefix = LockKeyConstraints.EXTRA_INTRO, keys = ["#userId"], waitTime = 0)
	@Transactional
	override fun introduce(userId: Long): Match {
		val requester: MatchUser = getMatchUserUseCase.findByUserId(userId)
			?: throw BusinessException(MatchErrorCode.MATCH_USER_NOT_MATCHABLE)

		val now: LocalDateTime = timeGenerator.now()
		val loginAfter: LocalDateTime = now.minusWeeks(RECENT_LOGIN_WEEKS)
		val today: LocalDate = now.toLocalDate()

		val candidates: List<ExtraIntroCandidateRow> =
			getExtraIntroCandidatePort.findCandidates(userId, requester.partnerGender(), loginAfter, today)
		val requesterProfile: MatchScoringProfile? = getExtraIntroCandidatePort.findRequesterProfile(userId, today)
		val nearby: List<Long> = getRegionProximityPort.nearbyRegionIds(requester.regionId)
		val rankByRegion: Map<Long, Int> = nearby.withIndex().associate { (index: Int, regionId: Long) -> regionId to index }

		val partner: ExtraIntroCandidateRow = MatchSelector.selectBest(
			targetProfile = requesterProfile,
			targetCompanyName = requester.companyName,
			targetRefusesSameCompanyIntro = requester.refuseSameCompanyIntro,
			candidates = candidates,
			profileOf = { row: ExtraIntroCandidateRow -> row.profile },
			regionRankByRegionId = rankByRegion,
			now = now,
			loginAfter = loginAfter,
			random = random,
			isExcluded = { row: ExtraIntroCandidateRow -> getExtraIntroCandidatePort.existsIntroduced(userId, row.userId) },
		) ?: throw BusinessException(MatchErrorCode.EXTRA_INTRO_NO_CANDIDATE)

		// 후보를 확정한 뒤에만 차감한다. (후보 없으면 차감 없음)
		spendCoinUseCase.spend(userId, SpendCoinCommand(amount = CoinUsageType.EXTRA_INTRO.coinAmount, coinUsageType = CoinUsageType.EXTRA_INTRO))

		val match: Match = Match.propose(
			requesterId = requester.userId,
			requesterGender = requester.gender,
			partnerId = partner.userId,
			matchType = SoloMatchType.EXTRA,
			now = now,
		)
		return saveMatchPort.save(match)
	}

	companion object {
		/** 후보로 인정하는 최근 로그인 기간(주). */
		private const val RECENT_LOGIN_WEEKS = 2L
	}
}
