package com.org.oneulsogae.scheduler.solomatch.query.dao

import com.org.oneulsogae.scheduler.solomatch.query.dto.MatchedUserIds
import java.time.LocalDate

/**
 * 배치가 매칭(소개) 이력을 조회하기 위한 dao. (조회 전용 — 기록은 [com.org.oneulsogae.scheduler.solomatch.command.application.port.out.SaveMatchRecordPort]가 담당)
 * 매칭 도메인/영속성은 core·infra가 갖고 있으므로, scheduler는 자기 관점의 이 dao만 정의하고
 * 실제 구현(core의 매칭 포트 위임)은 infra 어댑터가 담당한다. (scheduler는 core에 의존하지 않는다)
 */
interface GetMatchRecordDao {

	/** 두 사용자가 함께 소개된 이력이 있는지 여부. (재소개 방지용) */
	fun existsByPair(userIdA: Long, userIdB: Long): Boolean

	/**
	 * 성사(MATCHED)된 매칭에 속한 사용자 ID 전체를 한 번에 조회한다. (매칭의 남/녀 양쪽 ID를 모두 모은다)
	 * 배치 시작 시 한 번 적재해, 풀 적재·대상 순회에서 이미 매칭된 사용자를 제외하는 데 쓴다.
	 */
	fun findMatchedUserIds(): MatchedUserIds

	/**
	 * 주어진 날짜([date])에 일일 배치(DAILY)로 소개된(solo_matches.introduced_date = date) 매칭의 참가자 userId 집합.
	 * "오늘 일일 소개를 이미 받은 유저"를 신규 소개에서 제외하는 데 쓴다. (온보딩·추가소개는 일일 1회 소진으로 세지 않는다)
	 */
	fun findUserIdsIntroducedOn(date: LocalDate): Set<Long>
}
