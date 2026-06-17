package com.org.meeple.core.match.command.domain

import com.org.meeple.common.user.Gender
import java.time.LocalDateTime

/**
 * 매칭(소개)에 참가한 사용자 한 명의 참가 정보 도메인 모델.
 * 참가자를 (matchId, userId) 한 쌍의 행으로 정규화해, 1:1뿐 아니라 N:N(2:2·3:3) 미팅으로 확장한다.
 * [gender]는 성별 균형 구성·성별 기반 조회용, [accepted]는 이 참가자의 수락 여부다. (아직 응답 전이면 null)
 * [deletedAt]이 채워지면 소프트 삭제된(제거된) 참가자다.
 * 영속성은 [com.org.meeple.infra.match.command.entity.MatchMemberEntity]가 담당한다.
 */
data class MatchMember(
	val id: Long = 0,
	val matchId: Long,
	val userId: Long,
	val gender: Gender,
	val accepted: Boolean? = null,
	val deletedAt: LocalDateTime? = null,
) {

	/** 이 참가자가 수락했는지 여부. (응답 전이면 false) */
	val isAccepted: Boolean
		get() = accepted == true

	/** 이 참가자가 수락한 새 모델을 반환한다. */
	fun accept(): MatchMember =
		copy(accepted = true)

	/** 이 참가자를 [now]에 소프트 삭제(제거)한 새 모델을 반환한다. 저장하면 deletedAt이 채워져 조회에서 제외된다. */
	fun delete(now: LocalDateTime): MatchMember =
		copy(deletedAt = now)
}
