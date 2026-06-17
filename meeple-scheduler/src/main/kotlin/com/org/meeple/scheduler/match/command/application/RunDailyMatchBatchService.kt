package com.org.meeple.scheduler.match.command.application

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.command.application.port.`in`.RunDailyMatchBatchUseCase
import com.org.meeple.scheduler.match.command.application.port.out.SaveMatchPoolPort
import com.org.meeple.scheduler.match.command.application.port.out.TimeGenerator
import com.org.meeple.scheduler.match.command.domain.MatchBatchResult
import com.org.meeple.scheduler.match.command.domain.MatchPoolByGender
import com.org.meeple.scheduler.match.command.domain.MatchPoolGroup
import com.org.meeple.scheduler.match.query.dao.GetActiveUserDao
import com.org.meeple.scheduler.match.query.dao.GetMatchBatchTargetDao
import com.org.meeple.scheduler.match.query.dao.GetMatchRecordDao
import com.org.meeple.scheduler.match.query.dto.ActiveUser
import com.org.meeple.scheduler.match.query.dto.MatchBatchCursor
import com.org.meeple.scheduler.match.query.dto.MatchBatchTarget
import com.org.meeple.scheduler.match.query.dto.MatchedUserIds
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * [RunDailyMatchBatchUseCase] 구현.
 * 대상 사용자를 (lastLoginAt, userId) 커서 페이징으로 순회하며, 오늘 아직 소개가 없는 사용자에게 한 명을 소개한다.
 * 매칭에 필요한 프로필은 [MatchBatchTarget]에 이미 실려 오므로, 사용자별 추가 단건 조회 없이 처리한다.
 * 소개 상대는 미리 적재해 둔 (반대 성별, 같은 권역) Redis 풀에서 한 명씩 꺼내(pop) 재소개 이력이 없는 첫 후보로 정한다.
 * 전체를 하나의 트랜잭션으로 묶지 않으며, 한 사용자의 실패가 다른 사용자에게 전파되지 않도록 사용자 단위로 격리한다.
 * 예기치 못한 오류만 실패로 집계한다.
 */
@Service
class RunDailyMatchBatchService(
	private val getMatchBatchTargetDao: GetMatchBatchTargetDao,
	private val getMatchRecordDao: GetMatchRecordDao,
	private val getActiveUserDao: GetActiveUserDao,
	private val saveMatchPoolPort: SaveMatchPoolPort,
	private val matchIntroducer: MatchIntroducer,
	private val timeGenerator: TimeGenerator,
) : RunDailyMatchBatchUseCase {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	override fun run(): MatchBatchResult {
		val now: LocalDateTime = timeGenerator.now()
		val loginAfter: LocalDateTime = now.minusWeeks(RECENT_LOGIN_WEEKS)

		// 이미 성사(MATCHED)된 매칭에 속한 사용자 ID를 배치 시작에 한 번 적재한다. (풀 적재·대상 순회 양쪽에서 재사용)
		val matchedUserIds: MatchedUserIds = getMatchRecordDao.findMatchedUserIds()

		// 이번 배치 실행에서 이미 소개한 사용자 집합. (빈 Set으로 시작)
		// 실행 중 소개한 두 사람을 추가해, 같은 배치에서 한 사람이 두 번 소개되는 것만 막는다.
		// 온보딩 등 배치 외 당일 소개는 별개로 허용하므로, DB에서 당일 소개를 미리 적재하지 않는다.
		val introducedInThisRun: MutableSet<Long> = mutableSetOf()

		// 소개를 돌리기 전에 매칭 풀(권역별·성별)을 Redis에 적재하고, 적재에 쓴 활성 유저 목록을 받는다.
		val activeUsers: List<ActiveUser> = loadMatchPools(loginAfter, matchedUserIds)

		// 성별 풀에서 뽑은 상대의 권역을 알아내, 매칭 후 그 사람의 권역 풀에서도 빼주기 위한 조회표. (추가 쿼리 없이 메모리에서 산출)
		val regionByUserId: Map<Long, Int> = activeUsers.associate { user: ActiveUser -> user.userId to user.regionCode }

		// (lastLoginAt, userId) 복합 키셋 커서. 첫 페이지는 null로 시작한다.
		var cursor: MatchBatchCursor? = null
		var targets = 0
		var recommended = 0
		var skipped = 0
		var failed = 0

		while (true) {
			val page: List<MatchBatchTarget> = getMatchBatchTargetDao.findTargets(loginAfter, cursor, PAGE_SIZE)
			if (page.isEmpty()) break

			for (target: MatchBatchTarget in page) {
				val id: Long = target.userId
				targets++
				try {
					val gender: Gender? = target.gender
					if (gender == null) {
						// 성별 미입력은 매칭 대상 아님 (배치 쿼리가 이미 거르지만 방어적으로 확인)
						skipped++
						continue
					}
					val regionCode: Int? = target.regionCode
					if (regionCode == null) {
						// 활동 권역 미입력은 매칭 풀에 들어갈 수 없어 대상 아님
						skipped++
						continue
					}

					// 이미 성사(MATCHED)된 매칭이 있으면 신규 소개 대상이 아니므로 건너뛴다. (배치 시작에 적재한 집합으로 확인)
					if (matchedUserIds.contains(id)) {
						skipped++
						continue
					}

					// 이번 배치에서 이미 소개됐으면(다른 사용자의 상대로 소개됨) 건너뛴다. (메모리 집합 검사)
					if (introducedInThisRun.contains(id)) {
						skipped++
						continue
					}

					// 반대 성별·같은 권역 풀에서 재소개 이력 없는 후보를 찾아 소개한다. 후보가 없으면 이번엔 건너뛴다.
					val partnerId: Long? = matchIntroducer.introduce(id, gender, regionCode, regionByUserId, now)
					if (partnerId != null) {
						// 소개된 두 사람을 집합에 추가해, 상대가 뒤이어 대상이 될 때 이중 소개를 막는다.
						introducedInThisRun.add(id)
						introducedInThisRun.add(partnerId)
						recommended++
					} else {
						skipped++
					}
				} catch (e: Exception) {
					failed++
					log.warn("매칭 배치 처리 실패 userId={}", id, e)
				}
			}

			// 다음 페이지 커서 = 이번 페이지 마지막 대상의 (lastLoginAt, userId)
			val last: MatchBatchTarget = page.last()
			cursor = MatchBatchCursor(lastLoginAt = last.lastLoginAt, userId = last.userId)
		}

		val result = MatchBatchResult(targets = targets, recommended = recommended, skipped = skipped, failed = failed)
		log.info("일일 매칭 배치 완료: {}", result)
		return result
	}

	/**
	 * 매칭 풀을 적재하고, 적재에 쓴 활성 유저 목록을 반환한다.
	 * 활성 유저에서 이미 매칭(MATCHED)된 사용자를 빼고, (성별:지역) 그룹 + 지역 무관 성별 풀로 묶어 적재 순서 편향을 없애려 한 번 섞어 Redis에 적재한다.
	 * 성별 풀은 같은 권역 후보가 마른 경우의 폴백용이다. 반환한 목록은 이후 대상 순회의 권역 조회표 산출에 재사용한다.
	 */
	private fun loadMatchPools(loginAfter: LocalDateTime, matchedUserIds: MatchedUserIds): List<ActiveUser> {
		val activeUsers: List<ActiveUser> = matchedUserIds.exclude(getActiveUserDao.findActiveUsers(loginAfter))

		val groups: List<MatchPoolGroup> = MatchPoolGroup.group(activeUsers)
		groups.forEach { group: MatchPoolGroup -> saveMatchPoolPort.save(group.shuffled()) }

		val genderPools: List<MatchPoolByGender> = MatchPoolByGender.group(activeUsers)
		genderPools.forEach { pool: MatchPoolByGender -> saveMatchPoolPort.saveByGender(pool.shuffled()) }

		log.info(
			"활성 유저 매칭 풀 그룹핑 완료: groups={}, genderPools={}, activeUsers={}, matchedExcluded={}",
			groups.size, genderPools.size, activeUsers.size, matchedUserIds.size,
		)
		return activeUsers
	}

	companion object {
		/** 한 번에 조회·순회하는 대상 페이지 크기. */
		private const val PAGE_SIZE = 500

		/** 대상으로 인정하는 최근 로그인 기간(주). */
		private const val RECENT_LOGIN_WEEKS = 2L
	}
}
