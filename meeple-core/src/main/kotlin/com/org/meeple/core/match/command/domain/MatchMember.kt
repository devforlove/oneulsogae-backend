package com.org.meeple.core.match.command.domain

import com.org.meeple.common.user.Gender

/**
 * 매칭(소개)에 참가한 사용자 한 명의 참가 정보 도메인 모델.
 * 참가자를 (matchId, userId) 한 쌍의 행으로 정규화해, 1:1뿐 아니라 N:N(2:2·3:3) 미팅으로 확장한다.
 * [gender]는 성별 균형 구성·성별 기반 조회용, [accepted]는 이 참가자의 수락 여부다. (아직 응답 전이면 null)
 * 영속성은 [com.org.meeple.infra.match.command.entity.MatchMemberEntity]가 담당한다.
 */
data class MatchMember(
	val id: Long = 0,
	val matchId: Long,
	val userId: Long,
	val gender: Gender,
	val accepted: Boolean? = null,
) {

	/** 이 참가자가 수락했는지 여부. (응답 전이면 false) */
	val isAccepted: Boolean
		get() = accepted == true

	/** 이 참가자가 수락한 새 모델을 반환한다. */
	fun accept(): MatchMember =
		copy(accepted = true)
}
