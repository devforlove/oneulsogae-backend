package com.org.oneulsogae.core.solomatch.command.domain.event

/**
 * 상대가 관심을 보낸 매칭을 사용자가 목록 조회로 처음 확인했을 때 발행되는 도메인 이벤트.
 * 수신측이 확인 시각(checked_at) 기록과 상대방 "매칭 확인" 알람 저장에 필요한 식별 정보만 담는다.
 * (확인 시각이 이미 기록된 매칭은 발행되지 않으며, 동시 조회 중복은 수신측이 조건부 갱신으로 거른다)
 */
data class MatchChecked(
	val matchId: Long,
	/** 매칭을 확인한 사용자(조회자). */
	val checkedByUserId: Long,
	/** 확인 사실을 알람으로 받을 상대 참가자. */
	val partnerUserId: Long,
)
