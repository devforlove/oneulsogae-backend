package com.org.meeple.infra.lounge.command.adapter

import com.org.meeple.core.lounge.command.application.port.out.SaveSelfIntroPostPort
import com.org.meeple.core.lounge.command.domain.SelfIntroPost
import com.org.meeple.infra.lounge.command.mapper.toDomain
import com.org.meeple.infra.lounge.command.mapper.toEntity
import com.org.meeple.infra.lounge.command.repository.SelfIntroPostJpaRepository
import org.springframework.stereotype.Component

/** 셀소 본문 엔티티의 out-port 어댑터. (엔티티당 어댑터 하나) */
@Component
class SelfIntroPostAdapter(
	private val selfIntroPostJpaRepository: SelfIntroPostJpaRepository,
) : SaveSelfIntroPostPort {

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(post: SelfIntroPost): SelfIntroPost =
		selfIntroPostJpaRepository.save(post.toEntity()).toDomain()
}
