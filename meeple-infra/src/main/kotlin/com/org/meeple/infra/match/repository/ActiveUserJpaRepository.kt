package com.org.meeple.infra.match.repository

import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.user.entity.UserEntity
import com.org.meeple.scheduler.match.domain.ActiveUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/**
 * 매칭 풀 그룹핑용 활성 사용자 조회 전용 리포지토리.
 * users + user_details를 조인해 정식 가입(ACTIVE) + 최근 로그인 사용자를 한 번에 조회한다.
 * 이미 성사(MATCHED)된 매칭이 있는 사용자 제외는 배치 서비스가 MatchedUserIds로 따로 걸러낸다. (여기선 매칭 상태를 보지 않는다)
 * 그룹 키 산출에 필요한 (userId, gender, regionCode)만 [ActiveUser] 생성자 식으로 도메인 모델에 직접 투영한다.
 * 성별·권역 null 필터는 두지 않는다. ACTIVE 사용자는 [UserDetail.initProfile]/[editProfile] 검증으로 둘 다 채워짐이 보장되어,
 * non-null 생성자 식([ActiveUser])이 안전하다. (@SQLRestriction 으로 두 엔티티의 soft delete된 행은 자동 제외된다)
 */
interface ActiveUserJpaRepository : JpaRepository<UserEntity, Long> {

	@Query(
		"""
		select new com.org.meeple.scheduler.match.domain.ActiveUser(u.id, d.gender, d.regionCode)
		from UserEntity u
		join UserDetailEntity d on d.userId = u.id
		where u.status = :status
		  and u.lastLoginAt >= :loginAfter
		order by u.lastLoginAt asc, u.id asc
		""",
	)
	fun findActiveUsers(
		@Param("status") status: UserStatus,
		@Param("loginAfter") loginAfter: LocalDateTime,
	): List<ActiveUser>
}
