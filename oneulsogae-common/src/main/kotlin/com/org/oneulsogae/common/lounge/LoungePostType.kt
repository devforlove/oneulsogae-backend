package com.org.oneulsogae.common.lounge

/** 라운지 글([com.org.oneulsogae.infra.lounge.command.entity.LoungePostEntity])의 종류. */
enum class LoungePostType(val description: String) {

	/** 셀프 소개팅(셀소). 본문은 self_intro_posts가 보관한다. */
	SELF_INTRO("셀프 소개팅"),
}
