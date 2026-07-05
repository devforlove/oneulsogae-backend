package com.org.meeple.api.user

import com.org.meeple.api.user.response.CompanyImageVerificationResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.user.command.application.port.`in`.SubmitCompanyImageVerificationUseCase
import com.org.meeple.core.user.command.application.port.`in`.command.SubmitCompanyImageVerificationCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * 직장 서류 이미지 인증 엔드포인트. (인증 필요, 이메일 인증을 보완하는 추가 인증 수단)
 * - POST /company-image/verifications: 재직증명서 등 직장 서류 이미지를 업로드해 심사(PENDING)를 접수한다.
 *   파일은 S3에 비공개로 저장되고 DB에는 오브젝트 키만 남는다. 서류는 자동 검증이 불가능해 어드민 심사로 승인/반려된다.
 */
@RestController
@RequestMapping("/users/v1")
@Tag(name = "유저 직장 서류 인증", description = "직장 서류 이미지 업로드 인증 엔드포인트 (인증 필요)")
class UserCompanyImageVerificationController(
	private val submitCompanyImageVerificationUseCase: SubmitCompanyImageVerificationUseCase,
) {

	/** 직장 서류 이미지(JPEG·PNG·PDF, 최대 10MB)를 업로드해 심사를 접수한다. */
	@Operation(
		summary = "직장 서류 이미지 인증 제출",
		description = "재직증명서 등 직장 서류 이미지를 multipart/form-data(파트명 image)로 업로드한다. 파일은 S3에 비공개 저장되고, company_image_verifications에 오브젝트 키·심사 상태(PENDING)가 기록된다.",
	)
	@PostMapping("/company-image/verifications", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun submitCompanyImageVerification(
		@LoginUser user: AuthUser,
		@RequestParam("image") image: MultipartFile,
		@RequestParam("companyName") companyName: String,
	): ApiResponse<CompanyImageVerificationResponse> {
		val command = SubmitCompanyImageVerificationCommand(
			content = image.bytes,
			contentType = image.contentType,
			size = image.size,
			companyName = companyName,
		)
		return ApiResponse.success(
			CompanyImageVerificationResponse.of(submitCompanyImageVerificationUseCase.submit(user.id, command)),
		)
	}
}
