package com.org.oneulsogae.scheduler.lounge

import com.org.oneulsogae.scheduler.common.command.application.port.out.TimeGenerator
import com.org.oneulsogae.scheduler.lounge.command.application.ExpireLoungeChatRequestBatchService
import com.org.oneulsogae.scheduler.lounge.command.application.port.out.ExpireLoungeChatRequestPort
import com.org.oneulsogae.scheduler.lounge.command.application.port.out.GetExpiredLoungeChatRequestPort
import com.org.oneulsogae.scheduler.lounge.command.domain.ExpireLoungeChatRequestBatchResult
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [ExpireLoungeChatRequestBatchService] 유닛 테스트. 페이크 포트로 루프·건별 격리·집계를 검증한다.
 */
class ExpireLoungeChatRequestBatchServiceTest : DescribeSpec({

	val fixedNow: LocalDateTime = LocalDateTime.of(2026, 7, 23, 12, 0)
	val timeGenerator = object : TimeGenerator {
		override fun now(): LocalDateTime = fixedNow
	}

	describe("run") {
		it("만료 신청을 건별로 처리하고, 한 건이 실패해도 나머지를 처리하며 실패를 집계한다") {
			val getExpired = object : GetExpiredLoungeChatRequestPort {
				override fun findExpiredRequestIds(now: LocalDateTime): List<Long> = listOf(1L, 2L, 3L)
			}
			val processed = mutableListOf<Long>()
			val expire = object : ExpireLoungeChatRequestPort {
				override fun expire(requestId: Long) {
					if (requestId == 2L) throw RuntimeException("boom")
					processed.add(requestId)
				}
			}
			val service = ExpireLoungeChatRequestBatchService(getExpired, expire, timeGenerator)

			val result: ExpireLoungeChatRequestBatchResult = service.run()

			result.expired shouldBe 2
			result.failed shouldBe 1
			processed shouldBe listOf(1L, 3L)
		}
	}
})
