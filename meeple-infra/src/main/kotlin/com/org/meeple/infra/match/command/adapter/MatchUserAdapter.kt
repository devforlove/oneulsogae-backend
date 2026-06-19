package com.org.meeple.infra.match.command.adapter

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.command.application.port.out.DeleteMatchUserPort
import com.org.meeple.core.match.command.application.port.out.GetMatchCandidatePort
import com.org.meeple.core.match.command.application.port.out.GetMatchUserPort
import com.org.meeple.core.match.command.application.port.out.SaveMatchUserPort
import com.org.meeple.core.match.command.domain.MatchUser
import com.org.meeple.infra.match.command.mapper.toDomain
import com.org.meeple.infra.match.command.mapper.toEntity
import com.org.meeple.infra.match.command.repository.MatchUserJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.ThreadLocalRandom

/**
 * [MatchUserEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나 — 매칭 읽기 모델의 후보 조회/적재/삭제 out-port를 함께 구현)
 * 후보 선정([GetMatchCandidatePort])은 match_user 단독 조회로 수행한다(user_details 조인 제거).
 * 적재/삭제/조회([SaveMatchUserPort]/[DeleteMatchUserPort]/[GetMatchUserPort])는 user 도메인 이벤트 동기화에 쓰인다.
 */
@Component
class MatchUserAdapter(
	private val matchUserJpaRepository: MatchUserJpaRepository,
) : GetMatchCandidatePort, SaveMatchUserPort, GetMatchUserPort, DeleteMatchUserPort {

	/**
	 * 반대 성별·같은 활동 권역·최근 로그인 후보 수를 센 뒤, [0, count) 랜덤 오프셋으로 한 명만 뽑는다(LIMIT offset, 1).
	 * id 분포(탈퇴로 생긴 구멍)와 무관하게 각 후보가 1/N 확률로 균등하게 선택된다.
	 * (대규모에서는 offset 스캔 비용이 커지므로, 그때는 커서/시드 기반으로 재검토한다)
	 */
	override fun findOneCandidate(gender: Gender, regionCode: Int, loginAfter: LocalDateTime): Long? {
		val count: Long = matchUserJpaRepository.countCandidates(gender, regionCode, loginAfter)
		if (count == 0L) return null // 후보 풀이 비어 있음

		// [0, count) 랜덤 오프셋 1명 (PageRequest(page, size=1) -> LIMIT offset, 1)
		val offset: Int = ThreadLocalRandom.current().nextLong(count).toInt()
		return matchUserJpaRepository
			.findCandidateUserIds(gender, regionCode, loginAfter, PageRequest.of(offset, 1))
			.firstOrNull()
	}

	// user_id가 PK라 save가 upsert로 동작한다(없으면 INSERT, 있으면 UPDATE).
	override fun save(matchUser: MatchUser): MatchUser =
		matchUserJpaRepository.save(matchUser.toEntity()).toDomain()

	override fun updateLastLoginAt(userId: Long, lastLoginAt: LocalDateTime) {
		matchUserJpaRepository.updateLastLoginAt(userId, lastLoginAt)
	}

	override fun findByUserId(userId: Long): MatchUser? =
		matchUserJpaRepository.findById(userId).orElse(null)?.toDomain()

	override fun deleteByUserId(userId: Long) {
		matchUserJpaRepository.deleteByUserId(userId)
	}
}
