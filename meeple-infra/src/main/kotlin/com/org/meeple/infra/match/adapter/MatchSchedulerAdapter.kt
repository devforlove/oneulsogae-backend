package com.org.meeple.infra.match.adapter

import com.org.meeple.common.match.MatchType
import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.application.port.out.GetMatchPort
import com.org.meeple.core.match.application.port.out.SaveMatchPort
import com.org.meeple.core.match.domain.Match
import com.org.meeple.scheduler.match.application.port.out.MatchRecordPort
import com.org.meeple.scheduler.match.domain.MatchedUserIds
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * scheduler 모듈이 쓰는 [MatchEntity]의 영속성 어댑터. (매칭 엔티티당 scheduler용 어댑터는 이 하나)
 * scheduler의 [MatchRecordPort]를 core의 매칭 도메인/포트에 위임해 구현한다.
 * scheduler는 core에 의존하지 않으므로(자기 포트만 보유), core의 [Match]·매칭 포트를 아는 infra가 둘을 잇는다.
 * core 모듈용 어댑터는 [MatchCoreAdapter]/[MatchQueryCoreAdapter]가 별도로 둔다.
 */
@Component
class MatchSchedulerAdapter(
	private val getMatchPort: GetMatchPort,
	private val saveMatchPort: SaveMatchPort,
) : MatchRecordPort {

	override fun existsByPair(maleUserId: Long, femaleUserId: Long): Boolean =
		getMatchPort.existsByPair(maleUserId, femaleUserId)

	override fun existsByUserIdAndIntroducedDate(userId: Long, gender: Gender, date: LocalDate): Boolean =
		getMatchPort.existsByUserIdAndIntroducedDate(userId, gender, date)

	// core가 펼쳐 준 (남/녀 양쪽) 매칭 사용자 ID 리스트를 Set으로 정리해 일급 컬렉션으로 감싼다.
	override fun findMatchedUserIds(): MatchedUserIds =
		MatchedUserIds(getMatchPort.findMatchedUserIds().toSet())

	// 배치(scheduler)가 호출하는 경로이므로 일일 매칭(DAILY)으로 기록한다.
	override fun saveProposedMatch(requesterId: Long, requesterGender: Gender, partnerId: Long, now: LocalDateTime) {
		saveMatchPort.save(
			Match.propose(
				requesterId = requesterId,
				requesterGender = requesterGender,
				partnerId = partnerId,
				matchType = MatchType.DAILY,
				now = now,
			),
		)
	}
}
