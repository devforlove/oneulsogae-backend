package com.org.meeple.infra.match.adapter

import com.org.meeple.common.user.UserStatus
import com.org.meeple.scheduler.match.application.port.out.GetActiveUserPort
import com.org.meeple.scheduler.match.application.port.out.GetMatchBatchTargetPort
import com.org.meeple.scheduler.match.domain.ActiveUser
import com.org.meeple.scheduler.match.domain.MatchBatchCursor
import com.org.meeple.scheduler.match.domain.MatchBatchTarget
import com.org.meeple.infra.match.repository.ActiveUserJpaRepository
import com.org.meeple.infra.match.repository.MatchBatchTargetJpaRepository
import com.org.meeple.infra.match.repository.MatchBatchTargetView
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * scheduler 모듈이 쓰는 [UserEntity]의 영속성 어댑터.
 * 소개 배치가 보는 사용자 조회([GetActiveUserPort], [GetMatchBatchTargetPort])를 한곳에서 구현한다.
 * (UserEntity를 core 모듈에서도 쓰므로 모듈별로 어댑터를 나눈다. core용은 [UserCoreAdapter])
 */
@Component
class UserSchedulerAdapter(
	private val activeUserJpaRepository: ActiveUserJpaRepository,
	private val matchBatchTargetJpaRepository: MatchBatchTargetJpaRepository,
) : GetActiveUserPort, GetMatchBatchTargetPort {

	// 정식 가입·성별·지역 입력·최근 로그인 사용자. 쿼리가 [ActiveUser] 도메인 모델로 직접 투영하므로 그대로 반환한다.
	override fun findActiveUsers(loginAfter: LocalDateTime): List<ActiveUser> =
		activeUserJpaRepository.findActiveUsers(status = UserStatus.ACTIVE, loginAfter = loginAfter)

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
