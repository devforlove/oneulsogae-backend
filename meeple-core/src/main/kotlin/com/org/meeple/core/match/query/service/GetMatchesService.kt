package com.org.meeple.core.match.query.service

import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.match.query.service.port.`in`.GetMatchesUseCase
import com.org.meeple.core.match.command.application.port.`in`.RecommendMatchUseCase
import com.org.meeple.core.match.query.dao.GetMatchWithPartnerDao
import com.org.meeple.core.user.query.service.port.`in`.GetUserWithDetailUseCase
import com.org.meeple.core.match.query.dto.MatchWithPartner
import com.org.meeple.core.user.query.dto.UserWithDetailView
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [GetMatchesUseCase] 구현.
 * 사용자+프로필을 조인 한 번으로 가져와, **온보딩 직후([isAfterOnboarding] = true)** 이면 한 명을 자동 소개([RecommendMatchUseCase])한다.
 * 온보딩 직후에는 당일 매칭 이력이 있을 수 없으므로 별도의 "오늘 소개 여부" 검사는 하지 않는다.
 * 그 외(일반 조회)에는 신규 소개 없이 기존 매칭만 반환한다.
 * 이후 해당 사용자의 모든 매칭을 상대방 프로필과 조인해(1+N 없이) 함께 반환한다.
 * (성별이 없으면 매칭 대상이 아니므로 소개를 건너뛴다)
 */
@Service
class GetMatchesService(
	private val getUserWithDetailUseCase: GetUserWithDetailUseCase,
	private val getMatchWithPartnerDao: GetMatchWithPartnerDao,
	private val recommendMatchUseCase: RecommendMatchUseCase,
	private val timeGenerator: TimeGenerator,
) : GetMatchesUseCase {

	@Transactional
	override fun getMatches(userId: Long, isAfterOnboarding: Boolean): List<MatchWithPartner> {
		val userWithDetail: UserWithDetailView = getUserWithDetailUseCase.getByUserId(userId)

		// 온보딩 직후 사용자에게만 신규 매칭을 1건 만들어 준다.
		if (isAfterOnboarding) {
			recommendMatchUseCase.recommend(userWithDetail)
		}

		// 만료된 소개는 조회에서 제외한다. (포트/쿼리에서 now 기준으로 필터)
		val gender: Gender = userWithDetail.getGender()
		val now: LocalDateTime = timeGenerator.now()
		return getMatchWithPartnerDao.findAllWithPartnerByUserId(userId, gender, now)
	}
}
