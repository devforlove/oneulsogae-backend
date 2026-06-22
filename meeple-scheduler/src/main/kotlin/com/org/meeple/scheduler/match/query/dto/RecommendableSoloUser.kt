package com.org.meeple.scheduler.match.query.dto

import com.org.meeple.common.user.Gender

/**
 * 팀 추천을 받을 솔로 유저 read model. (match_user에 있으나 어떤 팀에도 속하지 않은 유저)
 * 후보 팀 선정에 필요한 성별·권역만 담는다. (둘 다 match_user에서 non-null)
 */
data class RecommendableSoloUser(
	val userId: Long,
	val gender: Gender,
	val regionCode: Int,
)
