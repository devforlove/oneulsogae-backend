package com.org.oneulsogae.core.solomatch.query.service

import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.matchuser.command.application.port.`in`.GetMatchUserUseCase
import com.org.oneulsogae.core.matchuser.command.domain.MatchUser
import com.org.oneulsogae.core.solomatch.query.dao.GetExtraIntroCandidateDao
import com.org.oneulsogae.core.solomatch.query.dto.ExtraIntroCandidate
import com.org.oneulsogae.core.solomatch.query.dto.ExtraIntroCandidates
import com.org.oneulsogae.core.solomatch.query.service.port.`in`.GetExtraIntroCandidatesUseCase
import com.org.oneulsogae.core.user.query.service.port.`in`.CheckCompanyVerifiedUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * [GetExtraIntroCandidatesUseCase] 구현. 자격 후보를 무작위로 섞어 상위 [DISPLAY_LIMIT]명의 표시 프로필과
 * 전체 자격 후보 수를 반환한다. 목록은 노출 시 마스킹되므로 스코어링·정렬은 하지 않는다. 부수효과 없는 순수 조회다.
 */
@Service
@Transactional(readOnly = true)
class GetExtraIntroCandidatesService(
	private val getMatchUserUseCase: GetMatchUserUseCase,
	private val getExtraIntroCandidateDao: GetExtraIntroCandidateDao,
	private val timeGenerator: TimeGenerator,
	private val checkCompanyVerifiedUseCase: CheckCompanyVerifiedUseCase,
	private val random: Random = Random.Default,
) : GetExtraIntroCandidatesUseCase {

	override fun getCandidates(userId: Long): ExtraIntroCandidates {
		// 회사 인증 여부는 user 도메인 in-port로 읽어 화면 분기용 플래그로 함께 내려준다.
		// (미인증이면 추가 소개 받기가 403이므로, 프론트엔드가 시도 전에 인증 안내로 막는다)
		val companyVerified: Boolean = checkCompanyVerifiedUseCase.isCompanyVerified(userId)

		// 매칭 가능 상태가 아니면 후보도 없다. (읽기 모델 미적재)
		val requester: MatchUser = getMatchUserUseCase.findByUserId(userId)
			?: return ExtraIntroCandidates(totalCount = 0, candidates = emptyList(), companyVerified = companyVerified, requesterGender = null)

		val loginAfter: LocalDateTime = timeGenerator.now().minusWeeks(RECENT_LOGIN_WEEKS)
		val eligibleUserIds: List<Long> =
			getExtraIntroCandidateDao.findEligibleCandidateIds(
				requesterId = userId,
				partnerGender = requester.partnerGender(),
				loginAfter = loginAfter,
				requesterCompanyName = requester.companyName,
				requesterRefusesSameCompanyIntro = requester.refuseSameCompanyIntro,
			)
		if (eligibleUserIds.isEmpty()) return ExtraIntroCandidates(totalCount = 0, candidates = emptyList(), companyVerified = companyVerified, requesterGender = requester.gender)

		// 목록은 마스킹되어 노출되므로 무작위로 섞어 상위 일부만 표시한다. (스코어링·정렬 불필요)
		val pickedUserIds: List<Long> = eligibleUserIds.shuffled(random).take(DISPLAY_LIMIT)
		val profileByUserId: Map<Long, ExtraIntroCandidate> =
			getExtraIntroCandidateDao.findDisplayProfiles(pickedUserIds).associateBy { candidate: ExtraIntroCandidate -> candidate.userId }
		// 섞은 순서를 유지한다.
		val candidates: List<ExtraIntroCandidate> = pickedUserIds.mapNotNull { id: Long -> profileByUserId[id] }

		return ExtraIntroCandidates(totalCount = eligibleUserIds.size, candidates = candidates, companyVerified = companyVerified, requesterGender = requester.gender)
	}

	companion object {
		/** 응답으로 내려주는 후보 프로필 수. */
		private const val DISPLAY_LIMIT = 11
		/** 후보로 인정하는 최근 로그인 기간(주). */
		private const val RECENT_LOGIN_WEEKS = 2L
	}
}
