package com.org.oneulsogae.api.admin

import com.org.oneulsogae.admin.gathering.command.application.port.`in`.ChangeGatheringStatusUseCase
import com.org.oneulsogae.admin.gathering.command.application.port.`in`.CreateAdminGatheringUseCase
import com.org.oneulsogae.admin.gathering.command.application.port.`in`.UpdateAdminGatheringUseCase
import com.org.oneulsogae.admin.gathering.query.service.port.`in`.GetAdminGatheringsUseCase
import com.org.oneulsogae.api.admin.request.ChangeGatheringStatusRequest
import com.org.oneulsogae.api.admin.request.CreateAdminGatheringRequest
import com.org.oneulsogae.api.admin.request.UpdateAdminGatheringRequest
import com.org.oneulsogae.api.admin.response.AdminGatheringDetailResponse
import com.org.oneulsogae.api.admin.response.AdminGatheringPageResponse
import com.org.oneulsogae.api.admin.response.CreateAdminGatheringResponse
import com.org.oneulsogae.common.gathering.GatheringStatus
import com.org.oneulsogae.common.gathering.GatheringType
import com.org.oneulsogae.core.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * 어드민 모임 엔드포인트. `/admin` 하위는 SecurityConfig의 hasRole(ADMIN)으로 보호된다.
 * - GET /: 최신순 page·size 페이징 목록 (소개·참가비 상세 제외).
 * - GET /{id}: 상세(소개·참가비 상세 + 모임 일정 목록 포함). 없으면 404(GATHER-008).
 * - POST /: 종류·제목·소개·지역·인원으로 모임 생성. (운영 생성 → user_id null, 활성화(RECRUITING)로 시작)
 * - POST /{id}: 모임 전체 데이터 수정(교체). 이미지 파트가 없으면 기존 이미지 유지. 없으면 404(GATHER-008).
 * - POST /{id}/status: 상태 변경(활성화 RECRUITING·취소 CANCELED). 없으면 404(GATHER-008), 전이 불가면 409(GATHER-013).
 */
@Tag(name = "어드민 모임", description = "어드민 백오피스 모임 조회·등록. ROLE_ADMIN 토큰만 접근할 수 있다.")
// [모임 기능 미노출] 오프라인·모임(gathering) 기능은 출시 시점에 노출하지 않는다. @RestController 주석 처리로 빈 미등록(호출 시 404). 재노출 시 주석 해제.
// @RestController
@RequestMapping("/admin/v1/gatherings")
class AdminGatheringController(
	private val createAdminGatheringUseCase: CreateAdminGatheringUseCase,
	private val updateAdminGatheringUseCase: UpdateAdminGatheringUseCase,
	private val changeGatheringStatusUseCase: ChangeGatheringStatusUseCase,
	private val getAdminGatheringsUseCase: GetAdminGatheringsUseCase,
) {

	@Operation(
		summary = "모임 목록 조회",
		description = "모임을 저장 날짜(생성 시각) 최신순으로 page(0부터)·size 페이징 조회한다. " +
			"status(모임 상태)·type(모임 종류)으로 필터할 수 있다(없으면 전체). 목록 항목은 소개·참가비 상세를 포함하지 않는다.",
	)
	@GetMapping
	fun gatherings(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@RequestParam(required = false) status: GatheringStatus?,
		@RequestParam(required = false) type: GatheringType?,
	): ApiResponse<AdminGatheringPageResponse> =
		ApiResponse.success(AdminGatheringPageResponse.of(getAdminGatheringsUseCase.getGatherings(page, size, status, type)))

	@Operation(
		summary = "모임 상세 조회",
		description = "모임 한 건을 id로 조회한다(소개·참가비 상세 + 모임 일정 목록(schedules, 시작 시각 오름차순) 포함). 없으면 404(GATHER-008).",
	)
	@GetMapping("/{id}")
	fun gathering(
		@PathVariable id: Long,
	): ApiResponse<AdminGatheringDetailResponse> =
		ApiResponse.success(AdminGatheringDetailResponse.of(getAdminGatheringsUseCase.getGathering(id)))

	@Operation(
		summary = "모임 생성",
		description = "multipart/form-data로 등록한다. request 파트(application/json)에 종류·제목·소개·지역·일시·정원·참가비" +
			"(정상가 남/녀 필수, 얼리버드·할인가 남/녀 선택)를, image 파트(선택)에 대표 이미지(JPEG·PNG, 최대 5MB)를 담는다. " +
			"이미지는 비공개로 저장되고 조회 시 presigned URL로 내려간다. 운영이 만든 모임으로 저장된다.",
	)
	@PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun create(
		@RequestPart("request") @Valid request: CreateAdminGatheringRequest,
		@RequestPart(value = "image", required = false) image: MultipartFile?,
	): ApiResponse<CreateAdminGatheringResponse> {
		val command = request.toCommand(
			imageContent = image?.bytes,
			imageContentType = image?.contentType,
			imageSize = image?.size ?: 0,
		)
		return ApiResponse.success(CreateAdminGatheringResponse.of(createAdminGatheringUseCase.create(command)))
	}

	@Operation(
		summary = "모임 수정",
		description = "multipart/form-data로 모임 전체 데이터를 수정(교체)한다. request 파트(application/json)에 생성과 동일한 필드를, " +
			"image 파트(선택)에 새 대표 이미지를 담는다. image 파트가 없으면 기존 대표 이미지를 유지한다. " +
			"상태(status)는 바뀌지 않는다. 없으면 404(GATHER-008).",
	)
	@PostMapping("/{id}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun update(
		@PathVariable id: Long,
		@RequestPart("request") @Valid request: UpdateAdminGatheringRequest,
		@RequestPart(value = "image", required = false) image: MultipartFile?,
	): ApiResponse<Unit> {
		val command = request.toCommand(
			imageContent = image?.bytes,
			imageContentType = image?.contentType,
			imageSize = image?.size ?: 0,
		)
		updateAdminGatheringUseCase.update(id, command)
		return ApiResponse.success()
	}

	@Operation(
		summary = "모임 상태 변경",
		description = "모임 상태를 전이한다. status=CANCELED면 취소(활성화→취소)한다. (모임은 생성 시 이미 활성화 상태다) " +
			"없으면 404(GATHER-008), 전이 불가면 409(GATHER-013).",
	)
	@PostMapping("/{id}/status")
	fun changeStatus(
		@PathVariable id: Long,
		@RequestBody @Valid request: ChangeGatheringStatusRequest,
	): ApiResponse<Unit> {
		changeGatheringStatusUseCase.changeStatus(id, request.status!!)
		return ApiResponse.success()
	}
}
