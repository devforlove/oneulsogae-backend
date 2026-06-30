package com.org.meeple.core.solomatch.query.service

import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.solomatch.query.service.port.`in`.GetMatchesUseCase
import com.org.meeple.core.solomatch.query.dao.GetMatchWithPartnerDao
import com.org.meeple.core.user.query.service.port.`in`.GetUserWithDetailUseCase
import com.org.meeple.core.solomatch.query.dto.MatchWithPartner
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [GetMatchesUseCase] 구현.
 * 사용자의 모든 매칭을 상대방 프로필과 조인 한 번으로(1+N 없이) 가져와 반환한다. (만료된 소개는 now 기준으로 제외)
 * 부수효과 없는 순수 조회다 — 온보딩 직후 첫 매칭 자동 소개는 회사 이메일 인증 완료 시점([com.org.meeple.core.user.command.application.UserEventHandler])이 담당한다.
 * (성별은 상대 프로필 표시 조인에 쓰며, 매칭 대상이 아니어도 조회 자체는 막지 않는다)
 */
@Service
class GetMatchesService(
	private val getUserWithDetailUseCase: GetUserWithDetailUseCase,
	private val getMatchWithPartnerDao: GetMatchWithPartnerDao,
	private val timeGenerator: TimeGenerator,
) : GetMatchesUseCase {

	@Transactional(readOnly = true)
	override fun getMatches(userId: Long): List<MatchWithPartner> {
		// 상대 프로필 표시 조인에 필요한 요청자 성별은 user 도메인 in-port로 읽는다. (표시 경로는 user_details에 의존)
		val gender: Gender = getUserWithDetailUseCase.getByUserId(userId).getGender()
		val now: LocalDateTime = timeGenerator.now()
		// 최신 매칭부터 노출하도록 matchId 내림차순으로 정렬한다.
		return getMatchWithPartnerDao.findAllWithPartnerByUserId(userId, gender, now)
			.sortedByDescending { match: MatchWithPartner -> match.matchId }
	}
}
