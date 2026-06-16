package com.org.meeple.infra.match.query

import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.match.command.repository.MatchBatchTargetJpaRepository
import com.org.meeple.infra.match.command.repository.MatchBatchTargetView
import com.org.meeple.scheduler.match.query.dao.MatchBatchTargetQueryDao
import com.org.meeple.scheduler.match.query.dto.MatchBatchCursor
import com.org.meeple.scheduler.match.query.dto.MatchBatchTarget
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * scheduler [MatchBatchTargetQueryDao]의 구현. 매칭 배치 대상 키셋 페이징 조회를 [MatchBatchTargetJpaRepository]에 위임한다.
 * (repository는 command 아래에 있고, query→command 참조는 허용)
 */
@Component
class MatchBatchTargetQueryDaoImpl(
	private val matchBatchTargetJpaRepository: MatchBatchTargetJpaRepository,
) : MatchBatchTargetQueryDao {

	/**
	 * 정식 가입·최근 로그인 사용자를 (lastLoginAt, id) 복합 키셋으로 조회한다.
	 * `users(status, last_login_at, id)` 인덱스 범위 스캔이라 최근 로그인 구간만 보고 filesort도 없다.
	 * 커서 유무로 첫/다음 페이지 쿼리를 나눠 호출해 range seek가 깨지지 않게 한다.
	 */
	override fun findTargets(loginAfter: LocalDateTime, cursor: MatchBatchCursor?, limit: Int): List<MatchBatchTarget> {
		val views: List<MatchBatchTargetView> = if (cursor == null) {
			matchBatchTargetJpaRepository.findFirstTargets(
				status = UserStatus.ACTIVE,
				loginAfter = loginAfter,
				limit = Limit.of(limit),
			)
		} else {
			matchBatchTargetJpaRepository.findNextTargets(
				status = UserStatus.ACTIVE,
				cursorLastLogin = cursor.lastLoginAt,
				cursorId = cursor.userId,
				limit = Limit.of(limit),
			)
		}
		return views.map { view: MatchBatchTargetView ->
			MatchBatchTarget(
				userId = view.userId,
				lastLoginAt = view.lastLoginAt,
				gender = view.gender,
				age = view.age,
				maritalStatus = view.maritalStatus,
				regionCode = view.regionCode,
			)
		}
	}
}
