package com.org.meeple.infra.user.command.adapter

import com.org.meeple.core.user.command.application.port.out.GetIdealTypePort
import com.org.meeple.core.user.command.application.port.out.SaveIdealTypePort
import com.org.meeple.core.user.command.domain.UserIdealType
import com.org.meeple.infra.user.command.mapper.toDomain
import com.org.meeple.infra.user.command.mapper.toEntity
import com.org.meeple.infra.user.command.repository.UserIdealTypeJpaRepository
import org.springframework.stereotype.Component

/**
 * [com.org.meeple.infra.user.command.entity.UserIdealTypeEntity]의 command out-port 어댑터. (Spring Data 메서드 쿼리)
 * upsert 시 서비스가 [findByUserId]로 기존 행을 로드해 id를 보존하므로, 저장은 id 유무에 따라 INSERT/UPDATE로 갈린다.
 */
@Component
class UserIdealTypeCoreAdapter(
	private val userIdealTypeJpaRepository: UserIdealTypeJpaRepository,
) : GetIdealTypePort, SaveIdealTypePort {

	override fun findByUserId(userId: Long): UserIdealType? =
		userIdealTypeJpaRepository.findByUserId(userId)?.toDomain()

	override fun save(idealType: UserIdealType): UserIdealType =
		userIdealTypeJpaRepository.save(idealType.toEntity()).toDomain()
}
