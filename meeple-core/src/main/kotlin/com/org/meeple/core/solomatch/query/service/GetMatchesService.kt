package com.org.meeple.core.solomatch.query.service

import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.event.DomainEventPublisher
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.solomatch.command.domain.event.MatchChecked
import com.org.meeple.core.solomatch.query.service.port.`in`.GetMatchesUseCase
import com.org.meeple.core.solomatch.query.dao.GetMatchWithPartnerDao
import com.org.meeple.core.user.query.service.port.`in`.GetUserWithDetailUseCase
import com.org.meeple.core.solomatch.query.dto.MatchWithPartner
import com.org.meeple.core.solomatch.query.dto.MatchesWithPartner
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [GetMatchesUseCase] 구현.
 * 사용자의 모든 매칭을 상대방 프로필과 조인 한 번으로(1+N 없이) 가져와 반환한다. (만료된 소개는 now 기준으로 제외)
 * 조회 트랜잭션 자체는 쓰지 않는다 — 온보딩 직후 첫 매칭 자동 소개는 회사 이메일 인증 완료 시점([com.org.meeple.core.user.command.application.UserEventHandler])이 담당한다.
 * 단, "상대가 관심을 보냈는데 아직 확인 시각이 없는" 매칭은 [MatchChecked] 이벤트를 발행해,
 * 커밋 후 command 측([com.org.meeple.core.solomatch.command.application.MatchEventHandler])이 확인 시각 기록·상대 알람을 별도 트랜잭션으로 처리한다.
 * (성별은 상대 프로필 표시 조인에 쓰며, 매칭 대상이 아니어도 조회 자체는 막지 않는다)
 */
@Service
class GetMatchesService(
	private val getUserWithDetailUseCase: GetUserWithDetailUseCase,
	private val getMatchWithPartnerDao: GetMatchWithPartnerDao,
	private val timeGenerator: TimeGenerator,
	private val domainEventPublisher: DomainEventPublisher,
) : GetMatchesUseCase {

	@Transactional(readOnly = true)
	override fun getMatches(userId: Long): List<MatchWithPartner> {
		// 상대 프로필 표시 조인에 필요한 요청자 성별은 user 도메인 in-port로 읽는다. (표시 경로는 user_details에 의존)
		val gender: Gender = getUserWithDetailUseCase.getByUserId(userId).getGender()
		val now: LocalDateTime = timeGenerator.now()
		val matches: List<MatchWithPartner> = getMatchWithPartnerDao.findAllWithPartnerByUserId(userId, gender, now)

		// 상대가 관심을 보냈는데 아직 확인하지 않은 매칭은 확인 이벤트를 발행한다. (확인 시각 기록·상대 알람은 커밋 후 command 측이 처리)
		matches
			.filter { match: MatchWithPartner -> match.checkedAt == null && match.hasPartnerInterest }
			.forEach { match: MatchWithPartner ->
				domainEventPublisher.publish(MatchChecked(matchId = match.matchId, checkedByUserId = userId, partnerUserId = match.partnerUserId))
			}

		// 노출 순서 규칙(상태 우선순위 → 최신순)은 일급 컬렉션이 캡슐화한다.
		return MatchesWithPartner(matches).sortedForDisplay()
	}
}
