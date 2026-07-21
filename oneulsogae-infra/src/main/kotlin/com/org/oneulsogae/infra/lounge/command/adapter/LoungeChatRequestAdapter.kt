package com.org.oneulsogae.infra.lounge.command.adapter

import com.org.oneulsogae.core.lounge.command.application.port.out.GetLoungeChatRequestPort
import com.org.oneulsogae.core.lounge.command.application.port.out.SaveLoungeChatRequestPort
import com.org.oneulsogae.core.lounge.command.domain.LoungeChatRequest
import com.org.oneulsogae.infra.lounge.command.mapper.toDomain
import com.org.oneulsogae.infra.lounge.command.mapper.toEntity
import com.org.oneulsogae.infra.lounge.command.repository.LoungeChatRequestJpaRepository
import org.springframework.stereotype.Component

/** 라운지 대화 신청 엔티티의 out-port 어댑터. (엔티티당 어댑터 하나 — 조회·저장을 함께 구현) */
@Component
class LoungeChatRequestAdapter(
	private val loungeChatRequestJpaRepository: LoungeChatRequestJpaRepository,
) : GetLoungeChatRequestPort, SaveLoungeChatRequestPort {

	override fun existsByPostIdAndRequesterUserId(postId: Long, requesterUserId: Long): Boolean =
		loungeChatRequestJpaRepository.existsByPostIdAndRequesterUserId(postId, requesterUserId)

	override fun findById(requestId: Long): LoungeChatRequest? =
		loungeChatRequestJpaRepository.findById(requestId).orElse(null)?.toDomain()

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(request: LoungeChatRequest): LoungeChatRequest =
		loungeChatRequestJpaRepository.save(request.toEntity()).toDomain()
}
