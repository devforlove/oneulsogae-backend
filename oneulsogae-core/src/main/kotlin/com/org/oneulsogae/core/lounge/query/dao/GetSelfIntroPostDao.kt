package com.org.oneulsogae.core.lounge.query.dao

import com.org.oneulsogae.core.lounge.query.dto.SelfIntroPostDetailView
import com.org.oneulsogae.core.lounge.query.dto.SelfIntroPostView

/** 라운지 셀소 조회 dao. (조회 전용) */
interface GetSelfIntroPostDao {

	/**
	 * 셀소 글을 최신(postId 내림차순)순으로 최대 [limit]건 조회한다.
	 * [beforeId]를 주면 그보다 과거(postId 미만) 구간을 잇는다. (커서 페이징)
	 */
	fun findPage(beforeId: Long?, limit: Int): List<SelfIntroPostView>

	/** 셀소 상세 한 건. 글이 없거나 셀소 타입이 아니면 null. */
	fun findDetailByPostId(postId: Long): SelfIntroPostDetailView?

	/** 글에 붙은 사진의 S3 오브젝트 키를 노출 순서(display_order 오름차순)대로 조회한다. */
	fun findImageKeysByPostId(postId: Long): List<String>

	/**
	 * [requesterUserId]가 이 글에 이미 대화를 신청했는지 여부. (상세의 신청 버튼 상태 표시용)
	 * 상태(PENDING/ACCEPTED)는 구분하지 않는다 — 어느 쪽이든 다시 신청할 수 없기 때문이다.
	 */
	fun existsChatRequest(postId: Long, requesterUserId: Long): Boolean
}
