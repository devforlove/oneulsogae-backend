package com.org.meeple.api.offline

import com.org.meeple.api.offline.response.GatheringDetailResponse
import com.org.meeple.api.offline.response.GatheringGroupListResponse
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.gathering.query.service.port.`in`.GetGatheringsUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 오프라인(비인증 공개) 모임 엔드포인트. `/offline` 하위는 인증 토큰 없이 접근할 수 있다(SecurityConfig permitAll).
 * gathering 도메인(core query)에 의존한다.
 * - GET /: 모집중(RECRUITING) 모임을 모임 타입별로 그룹핑해 조회한다. (타입 3종 모두 포함, 타입 내 최신 등록순)
 * - GET /{id}: 모집중 모임 한 건의 상세(소개·인원·참가비 3티어)를 조회한다. 없거나 모집중이 아니면 404(GATHERING-001).
 */
@Tag(name = "오프라인 모임", description = "비인증 공개 모임 조회 엔드포인트. 모집중 모임을 타입별로 그룹핑해 내려준다.")
@RestController
@RequestMapping("/offline/v1/gatherings")
class OfflineGatheringController(
	private val getGatheringsUseCase: GetGatheringsUseCase,
) {

	@Operation(
		summary = "모집중 모임 목록(타입별 그룹) 조회",
		description = "모집중(RECRUITING) 모임을 모임 타입별 그룹으로 조회한다. 타입 3종(1:1 로테이션·쿠킹·파티)을 항상 모두 포함하고, " +
			"해당 타입 모임이 없으면 빈 배열이다. 각 그룹 내부는 최신 등록순으로 정렬한다. " +
			"항목은 imageUrl(presigned)·region(장소)·title(제목)을 포함한다. (인증 불필요)",
	)
	@GetMapping
	fun gatherings(): ApiResponse<GatheringGroupListResponse> =
		ApiResponse.success(GatheringGroupListResponse.from(getGatheringsUseCase.getGatherings()))

	@Operation(
		summary = "모집중 모임 상세 조회",
		description = "모집중(RECRUITING) 모임 한 건의 상세를 id로 조회한다. 소개·인원(최소/최대)·참가비 3티어" +
			"(정상가 남/녀, 얼리버드 남/녀+적용 인원, 할인가 남/녀)와 imageUrl(presigned)·region·title을 포함한다. " +
			"없거나 모집중이 아니면 404(GATHERING-001). (인증 불필요)",
	)
	@GetMapping("/{id}")
	fun gathering(
		@PathVariable id: Long,
	): ApiResponse<GatheringDetailResponse> =
		ApiResponse.success(GatheringDetailResponse.of(getGatheringsUseCase.getGathering(id)))
}
