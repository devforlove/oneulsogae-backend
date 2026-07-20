package com.org.oneulsogae.core.user.query.dto

import com.org.oneulsogae.common.user.Gender

/**
 * 사용자([UserView])와 프로필 상세([UserDetailView])를 함께 담는 조회 결과(read model).
 * 두 정보를 한 번에 필요로 하는 경우(예: 매칭 가능 여부 판정) 1+N 없이 조인 한 번으로 가져온다.
 * query 전용 view이며 command 도메인을 참조하지 않는다.
 */
data class UserWithDetailView(
	val user: UserView,
	val detail: UserDetailView,
) {

	/** 성별을 non-null로 반환한다. 성별이 채워진 사용자에서만 호출한다. (null이면 NPE) */
	fun getGender(): Gender = detail.gender!!
}
