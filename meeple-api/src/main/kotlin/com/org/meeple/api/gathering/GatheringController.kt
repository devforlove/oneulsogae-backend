package com.org.meeple.api.gathering

import com.org.meeple.api.gathering.request.CreateGatheringRequest
import com.org.meeple.api.gathering.response.CreateGatheringResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.gathering.command.application.port.`in`.CreateGatheringUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "모임", description = "모임 API. 모임을 생성한다.")
@RestController
@RequestMapping("/gatherings/v1")
class GatheringController(
	private val createGatheringUseCase: CreateGatheringUseCase,
) {

	@Operation(summary = "모임 생성", description = "로그인 사용자가 종류·제목·소개·일시·정원으로 모임을 만든다. 생성자는 모임의 주최자가 된다.")
	@PostMapping
	fun create(
		@LoginUser user: AuthUser,
		@RequestBody @Valid request: CreateGatheringRequest,
	): ApiResponse<CreateGatheringResponse> =
		ApiResponse.success(CreateGatheringResponse.of(createGatheringUseCase.create(request.toCommand(user.id))))
}
