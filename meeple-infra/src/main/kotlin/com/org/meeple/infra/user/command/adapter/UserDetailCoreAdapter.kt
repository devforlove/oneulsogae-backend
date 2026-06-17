package com.org.meeple.infra.user.command.adapter

import com.org.meeple.core.user.command.domain.UserDetail
import com.org.meeple.core.user.command.application.port.out.GetUserDetailPort
import com.org.meeple.core.user.command.application.port.out.SaveUserDetailPort
import com.org.meeple.infra.user.command.mapper.toDomain
import com.org.meeple.infra.user.command.mapper.toEntity
import com.org.meeple.infra.user.command.repository.UserDetailJpaRepository
import org.springframework.stereotype.Component

/**
 * core 모듈이 쓰는 [com.org.meeple.infra.user.command.entity.UserDetailEntity]의 out-port 어댑터. (Spring Data 메서드 쿼리)
 * 명령 흐름의 단건 로드([GetUserDetailPort])와 저장([SaveUserDetailPort])을 구현한다.
 * 조회용 프로필·사용자+프로필 조인은 query 쪽 QueryDSL 구현체([com.org.meeple.infra.user.query.UserDetailDaoImpl], [com.org.meeple.infra.user.query.UserWithDetailDaoImpl])가 따로 담당한다.
 */
@Component
class UserDetailCoreAdapter(
	private val userDetailJpaRepository: UserDetailJpaRepository,
) : GetUserDetailPort, SaveUserDetailPort {

	override fun findByUserId(userId: Long): UserDetail? =
		userDetailJpaRepository.findByUserId(userId)?.toDomain()

	override fun save(userDetail: UserDetail): UserDetail =
		userDetailJpaRepository.save(userDetail.toEntity()).toDomain()
}
