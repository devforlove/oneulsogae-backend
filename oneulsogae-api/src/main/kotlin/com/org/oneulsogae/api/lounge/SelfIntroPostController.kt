package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.api.lounge.response.SelfIntroPostDetailResponse
import com.org.oneulsogae.api.lounge.response.SelfIntroPostPageResponse
import com.org.oneulsogae.api.lounge.response.SelfIntroPostResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.lounge.command.application.port.`in`.RegisterSelfIntroPostUseCase
import com.org.oneulsogae.core.lounge.command.application.port.`in`.command.RegisterSelfIntroPostCommand
import com.org.oneulsogae.core.lounge.query.service.port.`in`.GetSelfIntroPostsUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * 라운지 셀프 소개팅(셀소) 엔드포인트. (인증 필요)
 * - POST /lounge/v1/self-intro-posts: 사진(1~5장)과 본문을 업로드해 셀소 글을 등록한다.
 *   사진은 S3에 비공개로 저장되고 DB에는 오브젝트 키만 남는다. 등록은 최근 24시간에 1건으로 제한한다.
 * - GET /lounge/v1/self-intro-posts: 라운지 그리드용 목록을 최신순 24개씩 커서 페이징으로 조회한다.
 * - GET /lounge/v1/self-intro-posts/{postId}: 셀소 상세(프로필·본문·사진 전체)를 조회한다.
 */
@RestController
@RequestMapping("/lounge/v1")
@Tag(name = "라운지 셀소", description = "라운지 셀프 소개팅 등록·조회 엔드포인트 (인증 필요)")
class SelfIntroPostController(
	private val registerSelfIntroPostUseCase: RegisterSelfIntroPostUseCase,
	private val getSelfIntroPostsUseCase: GetSelfIntroPostsUseCase,
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

	/** 라운지 그리드용 셀소 목록을 최신순 한 페이지(24개) 조회한다. */
	@Operation(
		summary = "셀소 목록 조회",
		description = "라운지 그리드용 셀소 목록을 최신순으로 24개씩 내려준다. 각 항목은 글 식별자(postId)·작성자 닉네임·좋아요 수·대표 사진 열람용 URL(presigned)을 담는다. 다음 페이지는 응답의 nextCursor를 cursor 파라미터로 그대로 넘겨 조회한다(hasNext=false면 마지막 페이지).",
	)
	@GetMapping("/self-intro-posts")
	fun getSelfIntroPosts(
		@RequestParam("cursor", required = false) cursor: Long?,
	): ApiResponse<SelfIntroPostPageResponse> =
		ApiResponse.success(SelfIntroPostPageResponse.of(getSelfIntroPostsUseCase.getPosts(cursor)))

	/** 셀소 상세 한 건을 조회한다. */
	@Operation(
		summary = "셀소 상세 조회",
		description = "셀소 한 건의 작성자 프로필(닉네임·성별·만 나이·키·활동지역·직업)·본문 7개 항목·사진 전체(열람용 presigned URL, 노출 순서)·좋아요 수를 조회한다. 글이 없거나 삭제됐으면 404(LOUNGE-008)를 반환한다.",
	)
	@GetMapping("/self-intro-posts/{postId}")
	fun getSelfIntroPost(
		@PathVariable("postId") postId: Long,
	): ApiResponse<SelfIntroPostDetailResponse> =
		ApiResponse.success(SelfIntroPostDetailResponse.of(getSelfIntroPostsUseCase.getPost(postId)))

	/** MultipartFile에서 core가 받는 원시 바이트·메타([RegisterSelfIntroPostCommand.FilePart])를 뽑는다. */
	private fun toFilePart(file: MultipartFile): RegisterSelfIntroPostCommand.FilePart =
		RegisterSelfIntroPostCommand.FilePart(
			content = file.bytes,
			contentType = file.contentType,
			size = file.size,
		)
}
