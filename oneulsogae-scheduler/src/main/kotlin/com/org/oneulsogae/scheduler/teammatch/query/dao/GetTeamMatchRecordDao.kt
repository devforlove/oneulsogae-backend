package com.org.oneulsogae.scheduler.teammatch.query.dao

import com.org.oneulsogae.scheduler.teammatch.query.dto.MatchedTeamIds
import java.time.LocalDate

/**
 * 배치가 팀 매칭(소개) 이력을 조회하기 위한 dao. (조회 전용 — 기록은 [com.org.oneulsogae.scheduler.teammatch.command.application.port.out.SaveTeamMatchRecordPort]가 담당)
 * 팀 매칭 도메인/영속성은 core·infra가 갖고 있으므로, scheduler는 자기 관점의 이 dao만 정의하고
 * 실제 구현(core의 팀 매칭 위임)은 infra 어댑터가 담당한다. (scheduler는 core에 의존하지 않는다)
 */
interface GetTeamMatchRecordDao {

	/** 두 팀이 함께 소개된 이력이 있는지 여부. (재소개 방지용 — team_matches.member_key) */
	fun existsByPair(teamIdA: Long, teamIdB: Long): Boolean

	/**
	 * 성사(MATCHED)된 팀 매칭에 '활성(ACTIVE) 참가 팀으로' 속한 팀 ID 전체를 한 번에 조회한다.
	 * 배치 시작 시 한 번 적재해, 풀 적재·대상 순회에서 이미 성사된 팀을 제외하는 데 쓴다.
	 */
	fun findMatchedTeamIds(): MatchedTeamIds

	/**
	 * 주어진 날짜([date])에 소개된(team_matches.introduced_date = date) 팀 ID 집합.
	 * "오늘 한 번이라도 소개된 팀"을 신규 소개에서 제외하는 데 쓴다.
	 */
	fun findTeamIdsIntroducedOn(date: LocalDate): Set<Long>
}
