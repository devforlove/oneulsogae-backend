package com.org.meeple.api.admin

import com.org.meeple.admin.gathering.query.service.port.`in`.GetAdminGatheringMembersUseCase
import com.org.meeple.api.admin.response.AdminGatheringMemberPageResponse
import com.org.meeple.common.gathering.GatheringMemberStatus
import com.org.meeple.core.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 어드민 전역 모임 참가 신청 조회 엔드포인트. `/admin` 하위는 SecurityConfig의 hasRole(ADMIN)으로 보호된다.
 * 모임·일정을 고르지 않고 상태별로 전체 참가 신청을 한 목록에서 조회한다(운영 큐). 각 행은 어느 모임·일정의
 * 신청인지(모임명·일정시각·scheduleId) 맥락을 담아, 그 scheduleId로 승인/거절·상세를 호출할 수 있다.
 * 승인/거절·상세 조회는 일정 단위 엔드포인트(AdminGatheringMemberController)를 그대로 사용한다.
 */
@Tag(name = "어드민 모임 참가 신청(전역)", description = "어드민 백오피스 전역 참가 신청 목록 조회. ROLE_ADMIN 토큰만 접근할 수 있다.")
@RestController
@RequestMapping("/admin/v1/gatherings/members")
class AdminGatheringMemberSearchController(
    private val getAdminGatheringMembersUseCase: GetAdminGatheringMembersUseCase,
) {

    @Operation(
        summary = "전역 참가 신청 목록 조회",
        description = "모임·일정 무관 전체 참가 신청을 신청 순으로 page(0부터)·size 페이징 조회한다. " +
            "status(PENDING/JOINED/REJECTED/CANCELED) 생략 시 전체. 각 행에 모임명·일정시각·scheduleId를 함께 담는다.",
    )
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: GatheringMemberStatus?,
    ): ApiResponse<AdminGatheringMemberPageResponse> =
        ApiResponse.success(
            AdminGatheringMemberPageResponse.of(
                getAdminGatheringMembersUseCase.searchMembers(page, size, status),
            ),
        )
}
