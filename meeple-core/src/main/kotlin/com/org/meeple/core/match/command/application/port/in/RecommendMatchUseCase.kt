package com.org.meeple.core.match.command.application.port.`in`

import com.org.meeple.core.match.command.domain.Match
import com.org.meeple.core.user.query.dto.UserWithDetailView

/**
 * 매칭 추천 인포트(유스케이스).
 * 요청자([userWithDetail])에게 반대 성별의 정식 가입 사용자 1명을 소개한다.
 * 이미 소개된 적 있는 상대는 제외하며, 소개하는 순간 매칭(PROPOSED)을 생성해 이력으로 남긴다.
 * 소개할 후보가 없으면 null을 반환한다. (가입/프로필 미완성, 저장 실패는 예외)
 */
interface RecommendMatchUseCase {

	fun recommend(userWithDetail: UserWithDetailView): Match?
}
