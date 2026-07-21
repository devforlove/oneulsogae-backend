package com.org.oneulsogae.core.lounge.query.service

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.lounge.LoungeErrorCode
import com.org.oneulsogae.core.lounge.query.dao.GetLoungeChatRequestDao
import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestPage
import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestView
import com.org.oneulsogae.core.lounge.query.service.port.`in`.GetLoungeChatRequestsUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetLoungeChatRequestsUseCase] 구현. (조회 전용 - 쓰기 부수효과 없음)
 * 조회 dao([GetLoungeChatRequestDao])에만 의존한다. 소유권 검증에 쓰는 글 작성자도 자기 dao로 읽는다.
 * (command의 GetLoungePostPort를 공유하지 않는다 — query는 command 포트를 참조하지 않는다)
 * 목록은 페이지 크기 + 1건을 읽어 다음 페이지 존재 여부를 판정한다. (COUNT 없이 커서 페이징)
 * 신청자의 만 나이는 [TimeGenerator]의 오늘 날짜로 계산한다.
 */
@Service
@Transactional(readOnly = true)
class GetLoungeChatRequestsService(
	private val getLoungeChatRequestDao: GetLoungeChatRequestDao,
	private val timeGenerator: TimeGenerator,
) : GetLoungeChatRequestsUseCase {

	override fun getRequests(userId: Long, postId: Long, cursor: Long?): LoungeChatRequestPage {
		val authorUserId: Long = getLoungeChatRequestDao.findAuthorUserIdByPostId(postId)
			?: throw BusinessException(LoungeErrorCode.SELF_INTRO_POST_NOT_FOUND, "셀소를 찾을 수 없습니다: $postId")
		if (authorUserId != userId) {
			throw BusinessException(LoungeErrorCode.LOUNGE_POST_NOT_OWNED)
		}

		val rows: List<LoungeChatRequestView> = getLoungeChatRequestDao.findPageByPostId(postId, cursor, PAGE_SIZE + 1)
		return LoungeChatRequestPage.of(rows, PAGE_SIZE).withAges(timeGenerator.today())
	}

	companion object {
		/** 한 페이지에 내려주는 신청 건수. */
		const val PAGE_SIZE: Int = 20
	}
}
