package com.org.oneulsogae.core.lounge.command.domain

import com.org.oneulsogae.common.lounge.LoungePostType

/**
 * 라운지 글의 공통 골격 도메인 모델. 타입별 본문(셀소 → [SelfIntroPost])과 사진([LoungePostImages])이 이 글에 붙는다.
 * [likeCount]는 목록 표시용 좋아요 총합, [viewCount]는 상세 조회수다. 둘 다 별도 유스케이스가
 * 원자 UPDATE로 증감시키는 비정규화 값이라 도메인 메서드로는 바꾸지 않는다. (신규 글은 0으로 시작)
 */
data class LoungePost(
	val id: Long = 0,
	val type: LoungePostType,
	val userId: Long,
	val likeCount: Int = 0,
	val viewCount: Int = 0,
) {

	companion object {

		/** 셀프 소개팅 글을 새로 만든다. */
		fun createSelfIntro(userId: Long): LoungePost =
			LoungePost(type = LoungePostType.SELF_INTRO, userId = userId)
	}
}
