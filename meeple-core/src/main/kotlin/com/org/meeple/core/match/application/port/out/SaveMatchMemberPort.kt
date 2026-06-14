package com.org.meeple.core.match.application.port.out

import com.org.meeple.core.match.domain.MatchMembers

/**
 * 매칭 참가자 저장 아웃포트.
 * 매칭 생성 시 참가자([MatchMembers])를 정규화 테이블(match_members)에 함께 기록한다. (1:1·N:N 공통 토대)
 * 실제 구현은 infra 레이어의 어댑터가 담당한다.
 */
interface SaveMatchMemberPort {

	fun saveAll(members: MatchMembers): MatchMembers
}
