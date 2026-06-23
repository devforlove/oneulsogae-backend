package com.org.meeple.scheduler.match.query.dto

import com.org.meeple.common.user.Gender

/**
 * 매칭 풀 그룹핑 대상이 되는 활성 사용자 read model.
 * 성별·지역(regionId)이 모두 채워진 ACTIVE + 최근 로그인 사용자만 담는다. (그룹 키가 둘 다 필요하므로 non-null)
 */
data class ActiveUser(
	val userId: Long,
	val gender: Gender,
	val regionId: Long,
)
