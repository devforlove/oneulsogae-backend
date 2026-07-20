package com.org.oneulsogae.core.teammatch.query.service.port.`in`

import com.org.oneulsogae.core.teammatch.query.dto.MeetingTab

/**
 * 미팅탭 화면 집계를 조회하는 유스케이스(인포트).
 * 추천 팀·받은 초대 개수·내 결성(ACTIVE) 팀을 한 번에 모아 반환한다.
 */
interface GetMeetingTabUseCase {

	fun get(userId: Long): MeetingTab
}
