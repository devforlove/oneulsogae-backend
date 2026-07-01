package com.org.meeple.api.user

import com.org.meeple.api.user.request.SaveIdealTypeRequest
import com.org.meeple.api.user.response.IdealTypeResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.user.command.application.port.`in`.SaveIdealTypeUseCase
import com.org.meeple.core.user.query.service.port.`in`.GetIdealTypeUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users/v1/ideal-type")
@Tag(name = "이상형 설정", description = "로그인 사용자의 이상형(매칭 선호) 조회 및 저장 엔드포인트")
class IdealTypeController(
	private val getIdealTypeUseCase: GetIdealTypeUseCase,
	private val saveIdealTypeUseCase: SaveIdealTypeUseCase,
) {

	/** 현재 로그인 사용자의 이상형을 조회한다. 미설정이면 전 항목 null("상관없음")로 내려준다. */
	@Operation(summary = "내 이상형 조회", description = "현재 로그인 사용자의 이상형을 조회한다. 미설정이면 전 항목이 null이다.")
	@GetMapping
	fun getMyIdealType(
		@LoginUser user: AuthUser,
	): ApiResponse<IdealTypeResponse> =
		ApiResponse.success(
			getIdealTypeUseCase.findByUserId(user.id)?.let(IdealTypeResponse::of) ?: IdealTypeResponse.empty(),
		)

	/** 현재 로그인 사용자의 이상형을 저장한다(신규 생성 또는 전체 교체). */
	@Operation(summary = "내 이상형 저장", description = "현재 로그인 사용자의 이상형을 저장(upsert)한다. 생략(null)한 항목은 '상관없음'으로 저장된다.")
	@PutMapping
	fun saveMyIdealType(
		@LoginUser user: AuthUser,
		@Valid @RequestBody request: SaveIdealTypeRequest,
	): ApiResponse<IdealTypeResponse> =
		ApiResponse.success(IdealTypeResponse.of(saveIdealTypeUseCase.save(user.id, request.toCommand())))
}
