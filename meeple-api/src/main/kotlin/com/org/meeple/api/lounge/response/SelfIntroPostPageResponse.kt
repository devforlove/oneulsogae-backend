package com.org.meeple.api.lounge.response

import com.org.meeple.core.lounge.query.dto.SelfIntroPostPage
import com.org.meeple.core.lounge.query.dto.SelfIntroPostView

/**
 * 라운지 셀소 목록(커서 페이지) 응답.
 * [nextCursor]를 다음 요청의 `cursor`로 그대로 넘기면 이어지는 페이지를 받는다. (다음 페이지가 없으면 null)
 */
data class SelfIntroPostPageResponse(
	val items: List<SelfIntroPostItemResponse>,
	val hasNext: Boolean,
	val nextCursor: Long?,
) {
	companion object {

		fun of(page: SelfIntroPostPage): SelfIntroPostPageResponse =
			SelfIntroPostPageResponse(
				items = page.values.map { view: SelfIntroPostView -> SelfIntroPostItemResponse.of(view) },
				hasNext = page.hasNext,
				nextCursor = page.nextCursor,
			)
	}
}

/** 라운지 그리드 타일 한 칸. 사진은 비공개 저장이라 열람용 presigned URL로 내려준다. */
data class SelfIntroPostItemResponse(
	val postId: Long,
	val authorNickname: String?,
	val likeCount: Int,
	val imageUrl: String?,
) {
	companion object {

		fun of(view: SelfIntroPostView): SelfIntroPostItemResponse =
			SelfIntroPostItemResponse(
				postId = view.postId,
				authorNickname = view.authorNickname,
				likeCount = view.likeCount,
				imageUrl = view.imageUrl,
			)
	}
}
