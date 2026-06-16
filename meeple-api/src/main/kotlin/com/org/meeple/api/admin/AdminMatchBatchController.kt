package com.org.meeple.api.admin

import com.org.meeple.api.admin.response.MatchBatchResponse
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.match.MatchErrorCode
import com.org.meeple.scheduler.match.application.MatchBatchJob
import com.org.meeple.scheduler.match.domain.MatchBatchResult
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 전용 매칭 배치 엔드포인트. (admin 경로는 SecurityConfig에서 ROLE_ADMIN으로 제한)
 * 크론과 동일한 진입점([MatchBatchJob])을 호출해 일일 매칭 배치를 즉시(동기) 실행한다.
 */
@RestController
@RequestMapping("/admin/v1")
class AdminMatchBatchController(
	private val matchBatchJob: MatchBatchJob,
) {

	/**
	 * 일일 매칭 배치를 즉시 실행하고 결과를 반환한다.
	 * 크론·다른 수동 트리거와 겹쳐 이미 실행 중이면 [MatchErrorCode.MATCH_BATCH_ALREADY_RUNNING](409)을 던진다.
	 */
	@PostMapping("/matches/batch")
	fun runMatchBatch(): ApiResponse<MatchBatchResponse> {
		val result: MatchBatchResult = matchBatchJob.run()
			?: throw BusinessException(MatchErrorCode.MATCH_BATCH_ALREADY_RUNNING)
		return ApiResponse.success(MatchBatchResponse.of(result))
	}
}
