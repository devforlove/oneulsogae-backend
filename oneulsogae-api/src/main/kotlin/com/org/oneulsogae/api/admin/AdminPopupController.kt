package com.org.oneulsogae.api.admin

import com.org.oneulsogae.admin.popup.command.application.port.`in`.CreateAdminPopupUseCase
import com.org.oneulsogae.admin.popup.command.application.port.`in`.UpdateAdminPopupUseCase
import com.org.oneulsogae.admin.popup.query.service.port.`in`.GetAdminPopupsUseCase
import com.org.oneulsogae.api.admin.request.AdminPopupRequest
import com.org.oneulsogae.api.admin.response.AdminPopupDetailResponse
import com.org.oneulsogae.api.admin.response.AdminPopupPageResponse
import com.org.oneulsogae.core.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * 어드민 팝업 엔드포인트. `/admin` 하위는 SecurityConfig의 hasRole(ADMIN)으로 보호된다.
 * 어드민 관리 대상은 전역 팝업뿐이다. (개인 팝업(환불 안내 등)은 시스템이 생성·정리하므로 목록·상세에서 제외)
 * - GET /: 노출 순서(display_order asc) page·size 페이징 목록 (상세 필드 제외, 이미지는 미리보기 URL).
 * - GET /{id}: 상세. 없으면 404(POPUP-001).
 * - POST /: 팝업 생성(multipart — request JSON + image 파일). 기간 역전 400(POPUP-002), 제거 유형 400(POPUP-003), 잘못된 이미지 400(POPUP-004/005).
 * - POST /{id}: 팝업 전체 수정(multipart). image 파트가 없으면 기존 이미지 유지. 없으면 404(POPUP-001).
 */
@Tag(name = "어드민 팝업", description = "어드민 백오피스 전역 팝업 조회·등록·수정. ROLE_ADMIN 토큰만 접근할 수 있다.")
@RestController
@RequestMapping("/admin/v1/popups")
class AdminPopupController(
	private val getAdminPopupsUseCase: GetAdminPopupsUseCase,
	private val createAdminPopupUseCase: CreateAdminPopupUseCase,
	private val updateAdminPopupUseCase: UpdateAdminPopupUseCase,
) {

	@Operation(
		summary = "팝업 목록 조회",
		description = "전역 팝업을 노출 순서(display_order 오름차순, 동률이면 id 내림차순)로 page(0부터)·size 페이징 조회한다. 목록 항목은 본문·링크·버튼을 포함하지 않고 이미지는 미리보기 URL(imageUrl)로 내려간다. 개인 팝업(환불 안내 등)은 제외된다.",
	)
	@GetMapping
	fun popups(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
	): ApiResponse<AdminPopupPageResponse> =
		ApiResponse.success(AdminPopupPageResponse.of(getAdminPopupsUseCase.getPopups(page, size)))

	@Operation(
		summary = "팝업 상세 조회",
		description = "전역 팝업 한 건을 id로 조회한다(본문·링크·버튼 포함, 이미지는 미리보기 URL(imageUrl)). 없거나 개인 팝업이면 404(POPUP-001).",
	)
	@GetMapping("/{id}")
	fun popup(
		@PathVariable id: Long,
	): ApiResponse<AdminPopupDetailResponse> =
		ApiResponse.success(AdminPopupDetailResponse.of(getAdminPopupsUseCase.getPopup(id)))

	@Operation(
		summary = "팝업 생성",
		description = "multipart/form-data로 등록한다. request 파트(application/json)에 제목·설명·노출 순서·링크·버튼·유형·노출 기간을, " +
			"image 파트(선택)에 팝업 이미지(JPEG·PNG, 최대 10MB)를 담는다. 이미지는 서버가 업로드하고 이미지 템플릿으로 저장해 조회 시 URL로 내려준다. " +
			"노출 기간을 생략하면 제한 없이 노출되고, 유형을 생략하면 NORMAL이다. 노출 종료가 시작보다 앞서면 400(POPUP-002), " +
			"1회 조회 후 제거되는 유형(환불 안내류)이면 400(POPUP-003), 잘못된 이미지면 400(POPUP-004/POPUP-005).",
	)
	@PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun create(
		@RequestPart("request") @Valid request: AdminPopupRequest,
		@RequestPart(value = "image", required = false) image: MultipartFile?,
	): ApiResponse<Unit> {
		createAdminPopupUseCase.create(
			request.toCommand(
				imageContent = image?.bytes,
				imageContentType = image?.contentType,
				imageSize = image?.size ?: 0,
			),
		)
		return ApiResponse.success()
	}

	@Operation(
		summary = "팝업 수정",
		description = "multipart/form-data로 전역 팝업 한 건의 전체 데이터를 교체한다(생성 시각·id 보존). request 파트에 생성과 동일한 필드를, " +
			"image 파트(선택)에 새 팝업 이미지를 담는다. image 파트가 없으면 기존 이미지를 유지한다. 없거나 개인 팝업이면 404(POPUP-001). 검증 규칙은 생성과 같다.",
	)
	@PostMapping("/{id}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun update(
		@PathVariable id: Long,
		@RequestPart("request") @Valid request: AdminPopupRequest,
		@RequestPart(value = "image", required = false) image: MultipartFile?,
	): ApiResponse<Unit> {
		updateAdminPopupUseCase.update(
			id,
			request.toCommand(
				imageContent = image?.bytes,
				imageContentType = image?.contentType,
				imageSize = image?.size ?: 0,
			),
		)
		return ApiResponse.success()
	}
}
