package com.org.meeple.scheduler.match.application.port.out

import com.org.meeple.scheduler.match.domain.ActiveUser
import java.time.LocalDateTime

/**
 * 매칭 풀 그룹핑을 위한 활성 사용자 조회 아웃포트.
 * 정식 가입(ACTIVE) + 성별·지역 입력 + 최근 로그인([loginAfter] 이후) 사용자 전체를 조회한다.
 * (이미 성사(MATCHED)된 매칭이 있는 사용자 제외는 배치 서비스가 MatchedUserIds로 따로 걸러낸다)
 * (그룹핑이 목적이라 페이징 없이 한 번에 가져오며, 키 산출에 필요한 성별·지역만 담은 경량 read model을 반환한다)
 */
interface GetActiveUserPort {

	fun findActiveUsers(loginAfter: LocalDateTime): List<ActiveUser>
}
