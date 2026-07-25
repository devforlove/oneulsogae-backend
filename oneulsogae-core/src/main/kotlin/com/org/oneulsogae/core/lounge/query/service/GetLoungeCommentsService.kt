package com.org.oneulsogae.core.lounge.query.service

import com.org.oneulsogae.core.lounge.query.dao.GetLoungeCommentDao
import com.org.oneulsogae.core.lounge.query.dto.LoungeCommentPage
import com.org.oneulsogae.core.lounge.query.dto.LoungeCommentView
import com.org.oneulsogae.core.lounge.query.service.port.`in`.GetLoungeCommentsUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetLoungeCommentsUseCase] 구현. (조회 전용 - 쓰기 부수효과 없음)
 * root 댓글은 페이지 크기 + 1건을 읽어 다음 페이지 존재 여부를 판정하고(COUNT 없이 커서 페이징),
 * 현재 페이지 root들의 대댓글은 전부 함께 조회한다. 없는 글은 빈 페이지가 내려간다.
 */
@Service
@Transactional(readOnly = true)
class GetLoungeCommentsService(
	private val getLoungeCommentDao: GetLoungeCommentDao,
) : GetLoungeCommentsUseCase {

	override fun getComments(userId: Long?, postId: Long, cursor: Long?): LoungeCommentPage {
		val roots: List<LoungeCommentView> = getLoungeCommentDao.findRootPage(postId, cursor, PAGE_SIZE + 1)
		val page: LoungeCommentPage = LoungeCommentPage.of(roots, PAGE_SIZE)
		val replies: List<LoungeCommentView> =
			if (page.values.isEmpty()) {
				emptyList()
			} else {
				getLoungeCommentDao.findRepliesByParentIds(page.values.map { root: LoungeCommentView -> root.commentId })
			}
		return page.withReplies(replies).withMine(userId)
	}

	companion object {
		/** 한 페이지에 내려주는 root 댓글 건수. (대댓글은 페이지에 포함된 root의 것을 전부 내려준다) */
		const val PAGE_SIZE: Int = 20
	}
}
