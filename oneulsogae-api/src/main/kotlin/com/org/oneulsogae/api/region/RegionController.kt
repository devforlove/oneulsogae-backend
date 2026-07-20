package com.org.oneulsogae.api.region

import com.org.oneulsogae.api.region.response.RegionResponse
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.region.query.service.port.`in`.GetRegionsUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 활동지역 조회 엔드포인트. 클라이언트는 id를 포함한 지역 목록을 받아, 다른 요청(프로필/팀 초대 등)에서 regionId로 지역을 지정한다.
 */
@Tag(name = "지역", description = "활동지역(시/도 + 시/군/구) 목록 조회 엔드포인트")
@RestController
@RequestMapping("/regions/v1")
class RegionController(
	private val getRegionsUseCase: GetRegionsUseCase,
) {

	/** 전체 활동지역 목록을 id와 함께 반환한다. */
	@Operation(summary = "활동지역 목록 조회", description = "전체 활동지역(시/도 + 시/군/구)을 id와 함께 반환한다. 클라이언트는 이 id(regionId)로 지역을 선택해 요청한다.")
	@GetMapping("/list")
	fun getRegions(): ApiResponse<List<RegionResponse>> =
		ApiResponse.success(RegionResponse.listOf(getRegionsUseCase.getAll()))
}
