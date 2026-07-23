package com.org.oneulsogae.core.lounge.query.dao

import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestView
import java.time.LocalDateTime

/** 대화 신청 목록 조회 dao. (조회 전용) */
interface GetLoungeChatRequestDao {

	/**
	 * [receiverUserId]가 자기 셀소로 받은 신청을 최신(requestId 내림차순)순으로 최대 [limit]건 조회한다.
	 * 내가 쓴 모든 셀소를 합산하며, 상대방 프로필은 신청자다.
	 * [beforeId]를 주면 그보다 과거(requestId 미만) 구간을 잇는다. (커서 페이징)
	 * PENDING 신청은 만료 시각(expired_at)이 [now] 이후인 것만 포함한다. (만료된 신청 제외 — ACCEPTED는 항상 포함)
	 */
	fun findReceivedPage(receiverUserId: Long, beforeId: Long?, limit: Int, now: LocalDateTime): List<LoungeChatRequestView>

	/**
	 * [requesterUserId]가 남의 셀소에 보낸 신청을 최신(requestId 내림차순)순으로 최대 [limit]건 조회한다.
	 * 상대방 프로필은 글 작성자다.
	 * [beforeId]를 주면 그보다 과거(requestId 미만) 구간을 잇는다. (커서 페이징)
	 * PENDING 신청은 만료 시각(expired_at)이 [now] 이후인 것만 포함한다. (만료된 신청 제외 — ACCEPTED는 항상 포함)
	 */
	fun findSentPage(requesterUserId: Long, beforeId: Long?, limit: Int, now: LocalDateTime): List<LoungeChatRequestView>
}
