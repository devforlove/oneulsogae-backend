package com.org.oneulsogae.core.solomatch.command.application.port.`in`

import com.org.oneulsogae.core.solomatch.command.domain.Match

/**
 * 매칭 추천 인포트(유스케이스).
 * 요청자([userId])에게 반대 성별·같은 활동 권역의 사용자 1명을 소개한다.
 * 이미 소개된 적 있는 상대는 제외하며, 소개하는 순간 매칭(PROPOSED)을 생성해 이력으로 남긴다.
 * 요청자가 아직 매칭 가능 상태가 아니거나 소개할 후보가 없으면 null을 반환한다. (저장 실패는 예외)
 */
interface RecommendMatchUseCase {

	fun recommend(userId: Long): Match?
}
