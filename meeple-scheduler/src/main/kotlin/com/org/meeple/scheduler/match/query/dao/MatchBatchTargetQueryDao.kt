package com.org.meeple.scheduler.match.query.dao

import com.org.meeple.scheduler.match.query.dto.MatchBatchCursor
import com.org.meeple.scheduler.match.query.dto.MatchBatchTarget
import java.time.LocalDateTime

/**
 * 매칭 배치 대상 조회 dao.
 * 정식 가입(ACTIVE) + 성별 입력 + 최근 로그인([loginAfter] 이후) 사용자를 (lastLoginAt, userId) 복합 키셋 페이징으로 조회한다.
 * `last_login_at` 인덱스 범위를 타고 최근 로그인 사용자만 스캔하므로, 전체 ACTIVE 풀이 커져도 검사 행 수가 늘지 않는다.
 * 정렬 키가 (lastLoginAt, userId)라 다음 페이지 커서를 만들려면 userId뿐 아니라 lastLoginAt도 함께 필요하다.
 */
interface MatchBatchTargetQueryDao {

	/** [cursor]가 null이면 첫 페이지. 이후엔 직전 페이지 마지막 대상으로 만든 [cursor]를 넘긴다. */
	fun findTargets(loginAfter: LocalDateTime, cursor: MatchBatchCursor?, limit: Int): List<MatchBatchTarget>
}
