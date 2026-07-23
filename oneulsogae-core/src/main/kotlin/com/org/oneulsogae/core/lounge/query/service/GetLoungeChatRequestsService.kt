package com.org.oneulsogae.core.lounge.query.service

import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.lounge.query.dao.GetLoungeChatRequestDao
import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestPage
import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestView
import com.org.oneulsogae.core.lounge.query.service.port.`in`.GetLoungeChatRequestsUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetLoungeChatRequestsUseCase] 구현. (조회 전용 - 쓰기 부수효과 없음)
 * 조회 dao([GetLoungeChatRequestDao])에만 의존한다.
 * 두 목록 모두 요청한 사용자 본인의 신청만 돌려주므로 별도 소유권 검증이 필요 없다. (기준 컬럼이 곧 본인)
 * 페이지 크기 + 1건을 읽어 다음 페이지 존재 여부를 판정한다. (COUNT 없이 커서 페이징)
 * 상대방 만 나이는 [TimeGenerator]의 오늘 날짜로 계산한다.
 * 만료된 PENDING 신청(expired_at 경과)은 두 목록 모두에서 제외한다.
 */
@Service
@Transactional(readOnly = true)
class GetLoungeChatRequestsService(
	private val getLoungeChatRequestDao: GetLoungeChatRequestDao,
	private val timeGenerator: TimeGenerator,
) : GetLoungeChatRequestsUseCase {

	override fun getReceived(userId: Long, cursor: Long?): LoungeChatRequestPage {
		val rows: List<LoungeChatRequestView> =
			getLoungeChatRequestDao.findReceivedPage(userId, cursor, PAGE_SIZE + 1, timeGenerator.now())
		return toPage(rows)
	}

	override fun getSent(userId: Long, cursor: Long?): LoungeChatRequestPage {
		val rows: List<LoungeChatRequestView> =
			getLoungeChatRequestDao.findSentPage(userId, cursor, PAGE_SIZE + 1, timeGenerator.now())
		return toPage(rows)
	}

	private fun toPage(rows: List<LoungeChatRequestView>): LoungeChatRequestPage =
		LoungeChatRequestPage.of(rows, PAGE_SIZE).withAges(timeGenerator.today())

	companion object {
		/** 한 페이지에 내려주는 신청 건수. */
		const val PAGE_SIZE: Int = 20
	}
}
