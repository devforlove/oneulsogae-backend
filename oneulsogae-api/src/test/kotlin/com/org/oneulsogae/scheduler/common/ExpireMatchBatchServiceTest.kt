package com.org.oneulsogae.scheduler.common

import com.org.oneulsogae.scheduler.common.command.application.ExpireMatchBatchService
import com.org.oneulsogae.scheduler.common.command.application.port.out.ExpireMatchPort
import com.org.oneulsogae.scheduler.common.command.application.port.out.GetExpiredMatchPort
import com.org.oneulsogae.scheduler.common.command.application.port.out.TimeGenerator
import com.org.oneulsogae.scheduler.common.command.domain.ExpireMatchBatchResult
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [ExpireMatchBatchService] 유닛 테스트. 페이크 포트로 루프·건별 격리·집계를 검증한다.
 */
class ExpireMatchBatchServiceTest : DescribeSpec({

	val fixedNow: LocalDateTime = LocalDateTime.of(2026, 6, 29, 12, 0)
	val timeGenerator = object : TimeGenerator {
		override fun now(): LocalDateTime = fixedNow
	}

	describe("run") {
		it("만료 솔로·팀을 건별로 처리하고, 한 건이 실패해도 나머지를 처리하며 실패를 집계한다") {
			val getExpired = object : GetExpiredMatchPort {
				override fun findExpiredSoloMatchIds(now: LocalDateTime): List<Long> = listOf(1L, 2L)
				override fun findExpiredTeamMatchIds(now: LocalDateTime): List<Long> = listOf(3L)
			}
			val processedSolo = mutableListOf<Long>()
			val processedTeam = mutableListOf<Long>()
			val expire = object : ExpireMatchPort {
				override fun expireSoloMatch(matchId: Long) {
					if (matchId == 2L) throw RuntimeException("boom")
					processedSolo.add(matchId)
				}
				override fun expireTeamMatch(teamMatchId: Long) {
					processedTeam.add(teamMatchId)
				}
			}
			val service = ExpireMatchBatchService(getExpired, expire, timeGenerator)

			val result: ExpireMatchBatchResult = service.run()

			result.soloExpired shouldBe 1
			result.soloFailed shouldBe 1
			result.teamExpired shouldBe 1
			result.teamFailed shouldBe 0
			processedSolo shouldBe listOf(1L)
			processedTeam shouldBe listOf(3L)
		}
	}
})
