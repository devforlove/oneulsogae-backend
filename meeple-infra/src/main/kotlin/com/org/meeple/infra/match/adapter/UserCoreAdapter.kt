package com.org.meeple.infra.match.adapter

import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.UserStatus
import com.org.meeple.core.match.application.port.out.GetMatchCandidatePort
import com.org.meeple.infra.match.repository.MatchCandidateJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.ThreadLocalRandom

/**
 * core 모듈이 쓰는 [UserEntity]의 영속성 어댑터.
 * 매칭 후보 사용자 조회([GetMatchCandidatePort])를 구현한다.
 * 반대 성별·같은 활동 권역·같은 결혼 여부·ACTIVE·최근 로그인 후보 수를 센 뒤, [0, count) 랜덤 오프셋으로 한 명만 뽑는다(LIMIT offset, 1).
 * id 분포(탈퇴로 생긴 구멍)와 무관하게 각 후보가 1/N 확률로 균등하게 선택된다.
 * (대규모에서는 offset 스캔 비용이 커지므로, 그때는 커서/시드 기반으로 재검토한다)
 * (UserEntity를 scheduler 모듈에서도 쓰므로 모듈별로 어댑터를 나눈다. scheduler용은 [UserSchedulerAdapter])
 */
@Component
class UserCoreAdapter(
	private val matchCandidateJpaRepository: MatchCandidateJpaRepository,
) : GetMatchCandidatePort {

	override fun findOneCandidate(gender: Gender, regionCode: Int, loginAfter: LocalDateTime): Long? {
		val count: Long = matchCandidateJpaRepository.countCandidates(
			status = UserStatus.ACTIVE,
			gender = gender,
			regionCode = regionCode,
			loginAfter = loginAfter,
		)
		if (count == 0L) return null // 후보 풀이 비어 있음

		// [0, count) 랜덤 오프셋 1명 (PageRequest(page, size=1) -> LIMIT offset, 1)
		val offset: Int = ThreadLocalRandom.current().nextLong(count).toInt()
		return matchCandidateJpaRepository.findCandidateUserIds(
			status = UserStatus.ACTIVE,
			gender = gender,
			regionCode = regionCode,
			loginAfter = loginAfter,
			pageable = PageRequest.of(offset, 1),
		).firstOrNull()
	}
}
