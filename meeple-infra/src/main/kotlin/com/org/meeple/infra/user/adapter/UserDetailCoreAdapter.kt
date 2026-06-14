package com.org.meeple.infra.user.adapter

import com.org.meeple.core.user.application.port.out.GetUserDetailPort
import com.org.meeple.core.user.application.port.out.GetUserWithDetailPort
import com.org.meeple.core.user.application.port.out.SaveUserDetailPort
import com.org.meeple.core.user.domain.UserDetail
import com.org.meeple.core.user.domain.UserWithDetail
import com.org.meeple.infra.user.entity.UserDetailEntity
import com.org.meeple.infra.user.entity.UserEntity
import com.org.meeple.infra.user.mapper.toDomain
import com.org.meeple.infra.user.mapper.toEntity
import com.org.meeple.infra.user.repository.UserDetailJpaRepository
import com.org.meeple.infra.user.repository.UserWithDetailJpaRepository
import org.springframework.stereotype.Component

/**
 * core 모듈이 쓰는 [UserDetailEntity]의 영속성 어댑터.
 * 프로필 상세 조회·저장([GetUserDetailPort], [SaveUserDetailPort])과 사용자+프로필 조인 조회([GetUserWithDetailPort])를 한곳에서 구현한다.
 * 엔티티/도메인 변환([UserDetailMapper])을 책임지며, 외부에는 도메인 모델만 노출한다.
 */
@Component
class UserDetailCoreAdapter(
	private val userDetailJpaRepository: UserDetailJpaRepository,
	private val userWithDetailJpaRepository: UserWithDetailJpaRepository,
) : GetUserDetailPort, SaveUserDetailPort, GetUserWithDetailPort {

	override fun findByUserId(userId: Long): UserDetail? =
		userDetailJpaRepository.findByUserId(userId)?.toDomain()

	override fun save(userDetail: UserDetail): UserDetail =
		userDetailJpaRepository.save(userDetail.toEntity()).toDomain()

	// 조인 한 번으로 사용자 + 프로필 상세를 함께 가져온다. 행은 [UserEntity, UserDetailEntity].
	override fun findWithDetailByUserId(userId: Long): UserWithDetail? =
		userWithDetailJpaRepository.findWithDetailByUserId(userId).firstOrNull()?.let { row: Array<Any> ->
			UserWithDetail(
				user = (row[0] as UserEntity).toDomain(),
				detail = (row[1] as UserDetailEntity).toDomain(),
			)
		}
}
