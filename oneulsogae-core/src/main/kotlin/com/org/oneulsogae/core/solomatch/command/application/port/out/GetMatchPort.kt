package com.org.oneulsogae.core.solomatch.command.application.port.out

import com.org.oneulsogae.core.solomatch.command.domain.Match

/**
 * 매칭 단건/존재 조회 아웃포트. (Spring Data 파생 쿼리·참가자 조인으로 충분한 단순 조회)
 * 도메인 모델([Match])만을 반환하며, 실제 구현은 infra 레이어의 어댑터가 담당한다.
 * 상대 프로필 조인 등 QueryDSL이 필요한 복잡 조회는 [GetMatchWithPartnerDao]로 분리한다.
 */
interface GetMatchPort {

	/** id로 매칭을 조회한다. 없으면 null. */
	fun findById(id: Long): Match?
}
