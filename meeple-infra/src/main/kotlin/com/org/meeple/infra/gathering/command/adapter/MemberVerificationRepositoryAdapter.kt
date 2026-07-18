package com.org.meeple.infra.gathering.command.adapter

import com.org.meeple.core.gathering.command.application.port.out.SaveMemberVerificationPort
import com.org.meeple.core.gathering.command.domain.MemberVerification
import com.org.meeple.infra.gathering.command.mapper.toDomain
import com.org.meeple.infra.gathering.command.mapper.toEntity
import com.org.meeple.infra.gathering.command.repository.MemberVerificationJpaRepository
import org.springframework.stereotype.Component

/**
 * 멤버 인증(본인인증) 엔티티의 out-port 어댑터. (엔티티당 어댑터 하나)
 * core [SaveMemberVerificationPort](제출 저장)를 구현한다. 조회는 query의
 * [com.org.meeple.infra.gathering.query.GetMemberVerificationDaoImpl]이 따로 구현한다.
 */
@Component
class MemberVerificationRepositoryAdapter(
	private val memberVerificationJpaRepository: MemberVerificationJpaRepository,
) : SaveMemberVerificationPort {

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(verification: MemberVerification): MemberVerification =
		memberVerificationJpaRepository.save(verification.toEntity()).toDomain()
}
