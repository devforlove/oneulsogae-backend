package com.org.meeple.core.match.command.service.port.out

import com.org.meeple.core.match.command.domain.Match

/**
 * 매칭 단건/존재 조회 아웃포트. (Spring Data 파생 쿼리·참가자 조인으로 충분한 단순 조회)
 * 도메인 모델([Match])만을 반환하며, 실제 구현은 infra 레이어의 어댑터가 담당한다.
 * 상대 프로필 조인 등 QueryDSL이 필요한 복잡 조회는 [MatchWithPartnerQueryDao]로 분리한다.
 */
interface GetMatchPort {

	/** id로 매칭을 조회한다. 없으면 null. */
	fun findById(id: Long): Match?

	/**
	 * 두 사용자([userIdA], [userIdB])가 함께 소개된 이력이 있는지 여부. (재소개 방지용)
	 * 참가자 조합의 정규화 키(member_key) 유니크 인덱스로 확인하며, 날짜와 무관하게 한 번이라도 소개됐으면 true.
	 */
	fun existsByPair(userIdA: Long, userIdB: Long): Boolean

	/**
	 * 성사(MATCHED)된 매칭에 속한 사용자 ID 전체를 조회한다.
	 * 이미 매칭된 사용자를 신규 소개 대상·후보에서 제외하는 용도. (중복은 호출 측에서 Set으로 정리한다)
	 */
	fun findMatchedUserIds(): List<Long>
}
