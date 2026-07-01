package com.org.meeple.core.solomatch.query.service.port.`in`

import com.org.meeple.core.solomatch.query.dao.dto.ExtraIntroCandidates

/** 추가 소개 자격 후보(상위 N명 + 전체 수) 조회 유스케이스. */
interface GetExtraIntroCandidatesUseCase {
	fun getCandidates(userId: Long): ExtraIntroCandidates
}
