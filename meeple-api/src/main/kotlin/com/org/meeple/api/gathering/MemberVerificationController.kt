package com.org.meeple.api.gathering

import com.org.meeple.api.gathering.response.MemberVerificationResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.gathering.command.application.port.`in`.SubmitMemberVerificationUseCase
import com.org.meeple.core.gathering.command.application.port.`in`.command.SubmitMemberVerificationCommand
import com.org.meeple.core.gathering.query.service.port.`in`.GetMyMemberVerificationUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * 멤버 인증(본인인증) 엔드포인트. 모임 참여를 위한 인증이라 gathering 도메인이 소유한다. (인증 필요)
 * - POST /member-verifications: 직업 정보(직종·직장명/직종/직급)와 사진 3종(얼굴·신분증·서류)을 업로드해 심사(PENDING)를 접수한다.
 *   파일은 S3에 비공개로 저장되고 DB에는 오브젝트 키만 남는다. 자동 검증이 불가능해 어드민 심사로 승인/반려된다.
 * - GET /member-verifications/me: 내 최신 제출 1건을 조회한다. (없으면 data null)
 */
@RestController
@RequestMapping("/gatherings/v1")
@Tag(name = "멤버 인증", description = "멤버 인증(본인인증) 제출·조회 엔드포인트 (인증 필요)")
class MemberVerificationController(
	private val submitMemberVerificationUseCase: SubmitMemberVerificationUseCase,
	private val getMyMemberVerificationUseCase: GetMyMemberVerificationUseCase,
) {

	/** 직업 정보와 사진 3종(얼굴·신분증 JPEG·PNG / 서류 JPEG·PNG·PDF, 각 최대 10MB)을 업로드해 심사를 접수한다. */
	@Operation(
		summary = "멤버 인증 제출",
		description = "multipart/form-data로 얼굴(faceImage)·신분증(idCardImage — 주민등록증·운전면허증·여권 등, 연령 인증 및 본인 확인용. 주민등록번호 뒷자리는 가려서 제출)·직장 인증 서류(documentImage) 파일과 직종(jobCategory, 최대 30자)·직장명/직종/직급(jobDetail, 최대 100자) 텍스트를 함께 보낸다. 파일은 S3에 비공개 저장되고, member_verifications에 오브젝트 키 3개·직업 정보·심사 상태(PENDING)가 기록된다.",
	)
	@PostMapping("/member-verifications", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun submitMemberVerification(
		@LoginUser user: AuthUser,
		@RequestParam("faceImage") faceImage: MultipartFile,
		@RequestParam("idCardImage") idCardImage: MultipartFile,
		@RequestParam("documentImage") documentImage: MultipartFile,
		@RequestParam("jobCategory", required = false) jobCategory: String?,
		@RequestParam("jobDetail", required = false) jobDetail: String?,
	): ApiResponse<MemberVerificationResponse> {
		val command = SubmitMemberVerificationCommand(
			face = toFilePart(faceImage),
			idCard = toFilePart(idCardImage),
			document = toFilePart(documentImage),
			jobCategory = jobCategory.orEmpty(),
			jobDetail = jobDetail.orEmpty(),
		)
		return ApiResponse.success(
			MemberVerificationResponse.of(submitMemberVerificationUseCase.submit(user.id, command)),
		)
	}

	/** 내 최신 멤버 인증 제출 1건을 조회한다. (제출 이력이 없으면 data null) */
	@Operation(
		summary = "내 멤버 인증 조회",
		description = "내 최신 멤버 인증 제출 1건(심사 상태·직업 정보·반려 사유)을 조회한다. 제출 이력이 없으면 data가 null이다.",
	)
	@GetMapping("/member-verifications/me")
	fun getMyMemberVerification(@LoginUser user: AuthUser): ApiResponse<MemberVerificationResponse?> =
		ApiResponse.success(
			getMyMemberVerificationUseCase.findLatest(user.id)?.let { MemberVerificationResponse.of(it) },
		)

	/** MultipartFile에서 core가 받는 원시 바이트·메타([SubmitMemberVerificationCommand.FilePart])를 뽑는다. */
	private fun toFilePart(file: MultipartFile): SubmitMemberVerificationCommand.FilePart =
		SubmitMemberVerificationCommand.FilePart(
			content = file.bytes,
			contentType = file.contentType,
			size = file.size,
		)
}
