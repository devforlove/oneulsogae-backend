package com.org.oneulsogae.api.admin

import com.org.oneulsogae.admin.gathering.command.application.port.`in`.UploadGatheringDescriptionImageUseCase
import com.org.oneulsogae.api.admin.response.UploadImageResponse
import com.org.oneulsogae.core.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * 모임 소개(Markdown)에 삽입할 이미지 업로드 엔드포인트. `/admin` 하위는 SecurityConfig의 hasRole(ADMIN)으로 보호된다.
 * 저장 후 오브젝트 key를 돌려주고, 어드민은 소개 본문에 `![](/images/{key})`로 참조한다.
 */
@Tag(name = "어드민 이미지", description = "모임 소개에 삽입할 이미지 업로드. ROLE_ADMIN 토큰만 접근할 수 있다.")
@RestController
@RequestMapping("/admin/v1/images")
class AdminImageController(
	private val uploadGatheringDescriptionImageUseCase: UploadGatheringDescriptionImageUseCase,
) {

	@Operation(
		summary = "소개 이미지 업로드",
		description = "multipart/form-data의 image 파트(JPEG·PNG, 최대 5MB)를 S3에 저장하고 오브젝트 key를 반환한다.",
	)
	@PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun upload(
		@RequestPart("image") image: MultipartFile,
	): ApiResponse<UploadImageResponse> =
		ApiResponse.success(
			UploadImageResponse(
				key = uploadGatheringDescriptionImageUseCase.execute(image.bytes, image.contentType, image.size),
			),
		)
}
