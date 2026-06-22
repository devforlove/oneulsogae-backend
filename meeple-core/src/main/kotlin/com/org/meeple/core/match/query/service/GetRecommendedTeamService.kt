package com.org.meeple.core.match.query.service

import com.org.meeple.core.match.query.dao.GetRecommendedTeamDao
import com.org.meeple.core.match.query.dto.RecommendedTeam
import com.org.meeple.core.match.query.service.port.`in`.GetRecommendedTeamUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetRecommendedTeamUseCase] 구현. (조회 전용)
 * 팀 없는 솔로 유저에게 추천된 팀을 dao로 조회한다. (query dao만 의존, command 포트·도메인 미참조)
 */
@Service
@Transactional(readOnly = true)
class GetRecommendedTeamService(
	private val getRecommendedTeamDao: GetRecommendedTeamDao,
) : GetRecommendedTeamUseCase {

	override fun get(userId: Long): RecommendedTeam? =
		getRecommendedTeamDao.findByUserId(userId)
}
