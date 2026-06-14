package com.org.meeple.scheduler.match.application.port.out

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.domain.MatchedUserIds
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 배치가 매칭(소개) 이력을 조회·기록하기 위한 아웃포트.
 * 매칭 도메인/영속성은 core·infra가 갖고 있으므로, scheduler는 자기 관점의 이 포트만 정의하고
 * 실제 구현(core의 Match·매칭 포트 위임)은 infra 어댑터가 담당한다. (scheduler는 core에 의존하지 않는다)
 */
interface MatchRecordPort {

	/** 해당 남녀 쌍으로 소개된 이력이 있는지 여부. (재소개 방지용) */
	fun existsByPair(maleUserId: Long, femaleUserId: Long): Boolean

	/** 해당 사용자가 [date]에 소개된 매칭이 있는지 여부. (하루 한 번 제약 확인용) */
	fun existsByUserIdAndIntroducedDate(userId: Long, gender: Gender, date: LocalDate): Boolean

	/**
	 * 성사(MATCHED)된 매칭에 속한 사용자 ID 전체를 한 번에 조회한다. (매칭의 남/녀 양쪽 ID를 모두 모은다)
	 * 배치 시작 시 한 번 적재해, 풀 적재·대상 순회에서 이미 매칭된 사용자를 제외하는 데 쓴다.
	 */
	fun findMatchedUserIds(): MatchedUserIds

	/**
	 * 신규 소개(PROPOSED) 매칭을 저장한다.
	 * 요청자([requesterId])의 성별([requesterGender])로 남/녀 자리를 배치하고, [now] 기준으로 소개 일자·만료 시각을 잡는다.
	 */
	fun saveProposedMatch(requesterId: Long, requesterGender: Gender, partnerId: Long, now: LocalDateTime)
}
