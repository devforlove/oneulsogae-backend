package com.org.oneulsogae.core.lounge.command.application.port.`in`.result

/** 셀프 소개팅 등록 결과. 생성된 라운지 글의 id를 돌려준다. (상세 조회 경로 `/lounge/{postId}`의 키) */
data class RegisterSelfIntroPostResult(
	val postId: Long,
)
