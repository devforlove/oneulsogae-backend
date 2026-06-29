package com.org.meeple.scheduler.teammatch.query.dto

import com.org.meeple.common.user.Gender

/**
 * 추천 후보가 되는 ACTIVE(결성) 팀 read model.
 * 풀 버킷 키(팀 성별·팀 활동권역)만 담는다. (teams에서 모두 non-null)
 */
data class CandidateTeam(
    val teamId: Long,
    val gender: Gender,
    val regionId: Long,
)
