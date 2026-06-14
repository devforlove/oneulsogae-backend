package com.org.meeple.core.match.domain

import com.org.meeple.common.user.Gender

/**
 * 매칭(소개)에 참가한 사용자 한 명의 참가 정보 도메인 모델.
 * 참가자를 (matchId, userId) 한 쌍의 행으로 정규화해, 1:1뿐 아니라 N:N(2:2·3:3) 미팅으로 확장할 토대를 만든다.
 * [gender]는 성별 균형(예: 2:2 = 남2·여2) 구성·성별 기반 조회를 위해 참가자마다 보관한다.
 * 현재는 확장 씨앗 단계로, 매칭 생성 시 [Match]의 male/female에서 파생해 함께 기록만 한다. (수락·조회·배치 로직은 아직 male/female 기준)
 * 영속성은 [com.org.meeple.infra.match.entity.MatchMemberEntity]가 담당한다.
 */
data class MatchMember(
	val id: Long = 0,
	val matchId: Long,
	val userId: Long,
	val gender: Gender,
)
