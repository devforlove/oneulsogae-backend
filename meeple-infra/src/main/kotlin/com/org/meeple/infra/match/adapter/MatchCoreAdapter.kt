package com.org.meeple.infra.match.adapter

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.application.port.out.GetMatchPort
import com.org.meeple.core.match.application.port.out.SaveMatchMemberPort
import com.org.meeple.core.match.application.port.out.SaveMatchPort
import com.org.meeple.core.match.domain.Match
import com.org.meeple.core.match.domain.MatchMembers
import com.org.meeple.infra.match.mapper.toDomain
import com.org.meeple.infra.match.mapper.toEntity
import com.org.meeple.infra.match.repository.MatchedPairView
import com.org.meeple.infra.match.repository.MatchJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * core 모듈이 쓰는 [MatchEntity]의 Spring Data 어댑터.
 * 단건/존재 조회([GetMatchPort])·저장([SaveMatchPort])을 `MatchJpaRepository`로 구현한다.
 * 신규 매칭 저장 시에는 참가자를 정규화 테이블(match_members)에도 함께 기록한다(확장 씨앗). 그 저장은 [SaveMatchMemberPort]에 위임한다.
 * QueryDSL이 필요한 조인 조회는 [MatchQueryCoreAdapter]가, scheduler 모듈용 어댑터는 [MatchSchedulerAdapter]가 별도로 둔다.
 */
@Component
class MatchCoreAdapter(
	private val matchJpaRepository: MatchJpaRepository,
	private val saveMatchMemberPort: SaveMatchMemberPort,
) : GetMatchPort, SaveMatchPort {

	override fun findById(id: Long): Match? =
		matchJpaRepository.findById(id).orElse(null)?.toDomain()

	// (male_user_id, female_user_id) 유니크 인덱스로 해당 쌍의 소개 이력 존재 여부만 확인한다.
	override fun existsByPair(maleUserId: Long, femaleUserId: Long): Boolean =
		matchJpaRepository.existsByMaleUserIdAndFemaleUserId(maleUserId, femaleUserId)

	// 사용자의 성별에 해당하는 컬럼(복합 인덱스)만 조회한다.
	override fun existsByUserIdAndIntroducedDate(userId: Long, gender: Gender, date: LocalDate): Boolean =
		when (gender) {
			Gender.MALE -> matchJpaRepository.existsByMaleUserIdAndIntroducedDate(userId, date)
			Gender.FEMALE -> matchJpaRepository.existsByFemaleUserIdAndIntroducedDate(userId, date)
		}

	// 성사(MATCHED) 매칭의 (남자 ID, 여자 ID) 쌍을 양쪽 모두 한 리스트로 펼쳐 반환한다. (중복 정리는 호출 측 Set이 맡는다)
	override fun findMatchedUserIds(): List<Long> =
		matchJpaRepository.findUserIdPairsByStatus(MatchStatus.MATCHED)
			.flatMap { pair: MatchedPairView -> listOf(pair.maleUserId, pair.femaleUserId) }

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	// 신규 매칭(INSERT)일 때만, 저장으로 얻은 id로 참가자(male→MALE, female→FEMALE)를 match_members에 함께 기록한다. (확장 씨앗)
	override fun save(match: Match): Match {
		val isNew: Boolean = match.id == 0L
		val saved: Match = matchJpaRepository.save(match.toEntity()).toDomain()
		if (isNew) {
			saveMatchMemberPort.saveAll(MatchMembers.from(saved))
		}
		return saved
	}
}
