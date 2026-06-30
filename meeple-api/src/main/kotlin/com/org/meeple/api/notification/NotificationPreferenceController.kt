package com.org.meeple.api.notification

import com.org.meeple.api.notification.request.UpdateNotificationPreferenceRequest
import com.org.meeple.api.notification.response.NotificationPreferenceResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.notification.command.application.port.`in`.SaveNotificationPreferenceUseCase
import com.org.meeple.core.notification.query.service.port.`in`.GetNotificationPreferenceUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 알림 설정 엔드포인트. (모두 인증 필요)
 * - GET /: 현재 로그인 사용자의 알림 설정 6개를 조회한다(없으면 기본값).
 * - PUT /: 6개 전체를 교체한다(full replace, 멱등 upsert).
 */
@Tag(name = "알림 설정", description = "마이탭 알림 설정 조회·저장. 향후 카카오 알림톡 전송 여부 판단에 사용된다.")
@RestController
@RequestMapping("/notification-preferences/v1")
class NotificationPreferenceController(
	private val getNotificationPreferenceUseCase: GetNotificationPreferenceUseCase,
	private val saveNotificationPreferenceUseCase: SaveNotificationPreferenceUseCase,
) {

	@Operation(summary = "내 알림 설정 조회", description = "현재 로그인 사용자의 알림 설정 6개를 조회한다. 설정한 적 없으면 기본값을 반환한다.")
	@GetMapping
	fun myPreference(
		@LoginUser user: AuthUser,
	): ApiResponse<NotificationPreferenceResponse> =
		ApiResponse.success(NotificationPreferenceResponse.from(getNotificationPreferenceUseCase.getByUserId(user.id)))

	@Operation(summary = "내 알림 설정 저장", description = "현재 로그인 사용자의 알림 설정 6개를 전체 교체한다. (멱등 upsert)")
	@PutMapping
	fun updatePreference(
		@LoginUser user: AuthUser,
		@RequestBody request: UpdateNotificationPreferenceRequest,
	): ApiResponse<Unit> {
		saveNotificationPreferenceUseCase.save(request.toCommand(user.id))
		return ApiResponse.success()
	}
}
