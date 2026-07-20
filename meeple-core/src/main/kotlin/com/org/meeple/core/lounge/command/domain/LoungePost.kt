package com.org.meeple.core.lounge.command.domain

import com.org.meeple.common.lounge.LoungePostType

/**
 * 라운지 글의 공통 골격 도메인 모델. 타입별 본문(셀소 → [SelfIntroPost])과 사진([LoungePostImages])이 이 글에 붙는다.
 * [likeCount]는 목록 표시용 좋아요 총합이며 좋아요 유스케이스가 증감시킨다. (이번 범위 밖 — 신규 글은 0으로 시작)
 */
data class LoungePost(
	val id: Long = 0,
	val type: LoungePostType,
	val userId: Long,
	val likeCount: Int = 0,
) {

	companion object {

		/** 셀프 소개팅 글을 새로 만든다. */
		fun createSelfIntro(userId: Long): LoungePost =
			LoungePost(type = LoungePostType.SELF_INTRO, userId = userId)
	}
}
