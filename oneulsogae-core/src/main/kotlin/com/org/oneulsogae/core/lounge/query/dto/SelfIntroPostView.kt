package com.org.oneulsogae.core.lounge.query.dto

/**
 * 라운지 셀소 목록 한 건(read model). 그리드 타일에 필요한 값만 담는다.
 * dao는 [imageKey]까지 채우고 [imageUrl]은 null로 둔다. 서비스가 presign 결과로 [imageUrl]을 채운다.
 * [authorNickname]은 프로필(user_details)에서 조인해 오며, 프로필이 없으면 null이다.
 */
data class SelfIntroPostView(
	val postId: Long,
	val authorNickname: String?,
	val likeCount: Int,
	val imageKey: String?,
	val imageUrl: String? = null,
) {
	/** dao 투영용 생성자. imageUrl은 서비스가 presign으로 채운다. */
	constructor(
		postId: Long,
		authorNickname: String?,
		likeCount: Int,
		imageKey: String?,
	) : this(postId, authorNickname, likeCount, imageKey, null)
}
