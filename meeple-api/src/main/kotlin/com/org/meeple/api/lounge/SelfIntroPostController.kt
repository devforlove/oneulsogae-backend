package com.org.meeple.api.lounge

import com.org.meeple.api.lounge.response.SelfIntroPostResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.lounge.command.application.port.`in`.RegisterSelfIntroPostUseCase
import com.org.meeple.core.lounge.command.application.port.`in`.command.RegisterSelfIntroPostCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * 라운지 셀프 소개팅(셀소) 엔드포인트. (인증 필요)
 * - POST /lounge/v1/self-intro-posts: 사진(1~5장)과 본문을 업로드해 셀소 글을 등록한다.
 *   사진은 S3에 비공개로 저장되고 DB에는 오브젝트 키만 남는다. 등록은 최근 24시간에 1건으로 제한한다.
 */
@RestController
@RequestMapping("/lounge/v1")
@Tag(name = "라운지 셀소", description = "라운지 셀프 소개팅 등록 엔드포인트 (인증 필요)")
class SelfIntroPostController(
	private val registerSelfIntroPostUseCase: RegisterSelfIntroPostUseCase,
) {

	/** 사진(JPEG·PNG, 1~5장, 장당 최대 10MB)과 본문 7개 항목을 받아 셀소 글을 등록한다. */
	@Operation(
		summary = "셀소 등록",
		description = "multipart/form-data로 사진(photos, 1~5장 — 보낸 순서가 노출 순서)과 본문(longDistance·desiredAge 최대 40자, mbti 최대 10자, marriageThought·preferredPartner·charmPoint·freeWord 각 최대 500자)을 함께 보낸다. 본문 항목은 모두 필수다. 사진은 S3에 비공개 저장되고 lounge_posts·self_intro_posts·lounge_post_images에 기록된다. 최근 24시간 안에 이미 등록했다면 429(LOUNGE-007)를 반환한다.",
	)
	@PostMapping("/self-intro-posts", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun registerSelfIntroPost(
		@LoginUser user: AuthUser,
		@RequestParam("photos", required = false) photos: List<MultipartFile>?,
		@RequestParam("longDistance", required = false) longDistance: String?,
		@RequestParam("desiredAge", required = false) desiredAge: String?,
		@RequestParam("mbti", required = false) mbti: String?,
		@RequestParam("marriageThought", required = false) marriageThought: String?,
		@RequestParam("preferredPartner", required = false) preferredPartner: String?,
		@RequestParam("charmPoint", required = false) charmPoint: String?,
		@RequestParam("freeWord", required = false) freeWord: String?,
	): ApiResponse<SelfIntroPostResponse> {
		val command = RegisterSelfIntroPostCommand(
			photos = photos.orEmpty().map { photo: MultipartFile -> toFilePart(photo) },
			longDistance = longDistance.orEmpty(),
			desiredAge = desiredAge.orEmpty(),
			mbti = mbti.orEmpty(),
			marriageThought = marriageThought.orEmpty(),
			preferredPartner = preferredPartner.orEmpty(),
			charmPoint = charmPoint.orEmpty(),
			freeWord = freeWord.orEmpty(),
		)
		return ApiResponse.success(
			SelfIntroPostResponse.of(registerSelfIntroPostUseCase.register(user.id, command)),
		)
	}

	/** MultipartFile에서 core가 받는 원시 바이트·메타([RegisterSelfIntroPostCommand.FilePart])를 뽑는다. */
	private fun toFilePart(file: MultipartFile): RegisterSelfIntroPostCommand.FilePart =
		RegisterSelfIntroPostCommand.FilePart(
			content = file.bytes,
			contentType = file.contentType,
			size = file.size,
		)
}
