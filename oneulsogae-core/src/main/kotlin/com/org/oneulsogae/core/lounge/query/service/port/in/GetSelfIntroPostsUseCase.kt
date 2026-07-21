package com.org.oneulsogae.core.lounge.query.service.port.`in`

import com.org.oneulsogae.core.lounge.query.dto.SelfIntroPostDetailView
import com.org.oneulsogae.core.lounge.query.dto.SelfIntroPostPage

/**
 * 라운지 셀소 조회 유스케이스.
 * 목록은 피드가 길어질 수 있어 커서 기반으로 한 페이지씩 내려준다.
 */
interface GetSelfIntroPostsUseCase {

	/**
	 * 셀소 목록 한 페이지를 최신순으로 조회한다. [cursor]를 주면 그보다 과거 구간을 잇는다.
	 * [userId]는 조회한 사용자로, 미수락 신청 건수·회사 인증 여부 등 개인화 값 산출에만 쓴다.
	 * 비로그인(null)이면 개인화 값은 0/false로 내려간다. (목록 자체는 공개)
	 */
	fun getPosts(userId: Long?, cursor: Long?): SelfIntroPostPage

	/**
	 * 셀소 상세 한 건을 조회한다. 없거나 삭제됐으면 404를 던진다.
	 * [userId]는 조회한 사용자로, 이 사용자가 이미 대화를 신청했는지([SelfIntroPostDetailView.chatRequestedByMe]) 판정에만 쓴다.
	 * 비로그인(null)이면 chatRequestedByMe는 항상 false다. (상세 자체는 공개)
	 */
	fun getPost(userId: Long?, postId: Long): SelfIntroPostDetailView
}
