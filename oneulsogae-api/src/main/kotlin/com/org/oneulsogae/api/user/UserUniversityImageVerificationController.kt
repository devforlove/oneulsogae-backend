package com.org.oneulsogae.api.user

import com.org.oneulsogae.api.user.response.UniversityImageVerificationResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.user.command.application.port.`in`.SubmitUniversityImageVerificationUseCase
import com.org.oneulsogae.core.user.command.application.port.`in`.command.SubmitUniversityImageVerificationCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * 학교 서류 이미지 인증 엔드포인트. (인증 필요, 학교 이메일 인증을 보완하는 추가 인증 수단)
 * - POST /university-image/verifications: 재학·졸업증명서 등 학교 서류 이미지를 업로드해 심사(PENDING)를 접수한다.
 *   파일은 S3에 비공개로 저장되고 DB에는 오브젝트 키만 남는다. 서류는 자동 검증이 불가능해 어드민 심사로 승인/반려된다.
 */
@RestController
@RequestMapping("/users/v1")
@Tag(name = "유저 학교 서류 인증", description = "학교 서류 이미지 업로드 인증 엔드포인트 (인증 필요)")
class UserUniversityImageVerificationController(
	private val submitUniversityImageVerificationUseCase: SubmitUniversityImageVerificationUseCase,
) {

	/** 학교 서류 이미지(JPEG·PNG·PDF, 최대 10MB)를 업로드해 심사를 접수한다. */
	@Operation(
		summary = "학교 서류 이미지 인증 제출",
		description = "재학·졸업증명서 등 학교 서류 이미지를 multipart/form-data(파트명 image)로 업로드하고, 인증받고자 하는 학교명을 파트명 universityName(필수·최대 50자)으로 함께 보낸다. 파일은 S3에 비공개 저장되고, university_image_verifications에 오브젝트 키·희망 학교명·심사 상태(PENDING)가 기록된다.",
	)
	@PostMapping("/university-image/verifications", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun submitUniversityImageVerification(
		@LoginUser user: AuthUser,
		@RequestParam("image") image: MultipartFile,
		@RequestParam("universityName", required = false) universityName: String?,
	): ApiResponse<UniversityImageVerificationResponse> {
		val command = SubmitUniversityImageVerificationCommand(
			content = image.bytes,
			contentType = image.contentType,
			size = image.size,
			universityName = universityName.orEmpty(),
		)
		return ApiResponse.success(
			UniversityImageVerificationResponse.of(submitUniversityImageVerificationUseCase.submit(user.id, command)),
		)
	}
}
