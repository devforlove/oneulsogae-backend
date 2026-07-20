package com.org.oneulsogae.core.lounge.command.domain

/**
 * 라운지 글 한 건에 붙은 사진 목록(일급 컬렉션). 노출 순서는 목록의 순서를 그대로 [LoungePostImage.displayOrder]로 굳힌다.
 */
data class LoungePostImages(
	val values: List<LoungePostImage>,
) {

	companion object {

		/** 업로드한 순서대로 노출 순서(0부터)를 매겨 [postId]에 붙일 사진 목록을 만든다. */
		fun of(postId: Long, imageKeys: List<String>): LoungePostImages =
			LoungePostImages(
				imageKeys.mapIndexed { index: Int, imageKey: String ->
					LoungePostImage(postId = postId, imageKey = imageKey, displayOrder = index)
				},
			)
	}
}

/** 라운지 글에 붙은 사진 한 장. 파일 자체는 S3에 있고 여기엔 오브젝트 키만 담는다. */
data class LoungePostImage(
	val id: Long = 0,
	val postId: Long,
	val imageKey: String,
	val displayOrder: Int,
)
