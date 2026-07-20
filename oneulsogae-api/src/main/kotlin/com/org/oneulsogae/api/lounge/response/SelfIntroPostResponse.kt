package com.org.oneulsogae.api.lounge.response

import com.org.oneulsogae.core.lounge.command.application.port.`in`.result.RegisterSelfIntroPostResult

/** 셀프 소개팅 등록 응답. 사진은 비공개 저장이라 URL 대신 생성된 글 식별자만 내려준다. */
data class SelfIntroPostResponse(
	val postId: Long,
) {
	companion object {

		fun of(result: RegisterSelfIntroPostResult): SelfIntroPostResponse =
			SelfIntroPostResponse(postId = result.postId)
	}
}
