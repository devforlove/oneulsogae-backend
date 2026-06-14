package com.org.meeple.core.match.application.port.out

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.domain.Match
import java.time.LocalDate

/**
 * 매칭 단건/존재 조회 아웃포트. (Spring Data 파생 쿼리로 충분한 단순 조회)
 * 도메인 모델([Match])만을 반환하며, 실제 구현은 infra 레이어의 어댑터가 담당한다.
 * 상대 프로필 조인 등 QueryDSL이 필요한 복잡 조회는 [GetMatchWithPartnerPort]로 분리한다.
 */
interface GetMatchPort {

	/** id로 매칭을 조회한다. 없으면 null. */
	fun findById(id: Long): Match?

	/**
	 * 해당 남녀 쌍([maleUserId], [femaleUserId])으로 소개된 이력이 있는지 여부. (재소개 방지용)
	 * (male_user_id, female_user_id) 유니크 인덱스를 그대로 타며, 날짜와 무관하게 한 번이라도 소개됐으면 true.
	 */
	fun existsByPair(maleUserId: Long, femaleUserId: Long): Boolean

	/**
	 * 해당 사용자가 [date]에 소개된 매칭이 있는지 여부. (하루 한 번 제약 확인용)
	 * 남녀 1:1이라 사용자는 자신의 성별([gender]) 컬럼에만 등장하므로, 그 한쪽만 조회한다.
	 */
	fun existsByUserIdAndIntroducedDate(userId: Long, gender: Gender, date: LocalDate): Boolean

	/**
	 * 성사(MATCHED)된 매칭에 속한 사용자 ID 전체를 조회한다. (각 매칭의 남자 ID·여자 ID를 모두 펼쳐 담는다)
	 * 이미 매칭된 사용자를 신규 소개 대상·후보에서 제외하는 용도. (중복은 호출 측에서 Set으로 정리한다)
	 */
	fun findMatchedUserIds(): List<Long>
}
