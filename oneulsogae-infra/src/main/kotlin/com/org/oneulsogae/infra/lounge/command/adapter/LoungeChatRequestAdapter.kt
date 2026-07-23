package com.org.oneulsogae.infra.lounge.command.adapter

import com.org.oneulsogae.core.lounge.command.application.port.out.DeleteLoungeChatRequestPort
import com.org.oneulsogae.core.lounge.command.application.port.out.GetLoungeChatRequestPort
import com.org.oneulsogae.core.lounge.command.application.port.out.SaveLoungeChatRequestPort
import com.org.oneulsogae.core.lounge.command.domain.LoungeChatRequest
import com.org.oneulsogae.infra.lounge.command.mapper.toDomain
import com.org.oneulsogae.infra.lounge.command.mapper.toEntity
import com.org.oneulsogae.infra.lounge.command.repository.LoungeChatRequestJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/** 라운지 대화 신청 엔티티의 out-port 어댑터. (엔티티당 어댑터 하나 — 조회·저장·삭제를 함께 구현) */
@Component
class LoungeChatRequestAdapter(
	private val loungeChatRequestJpaRepository: LoungeChatRequestJpaRepository,
) : GetLoungeChatRequestPort, SaveLoungeChatRequestPort, DeleteLoungeChatRequestPort {

	override fun existsByPostIdAndRequesterUserId(postId: Long, requesterUserId: Long): Boolean =
		loungeChatRequestJpaRepository.existsByPostIdAndRequesterUserId(postId, requesterUserId)

	override fun findById(requestId: Long): LoungeChatRequest? =
		loungeChatRequestJpaRepository.findById(requestId).orElse(null)?.toDomain()

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(request: LoungeChatRequest): LoungeChatRequest =
		loungeChatRequestJpaRepository.save(request.toEntity()).toDomain()

	// 이미 삭제됐거나 없는 행이면 아무것도 하지 않는다. (@SQLRestriction으로 삭제 행은 조회되지 않는다)
	override fun delete(requestId: Long, now: LocalDateTime) {
		val entity = loungeChatRequestJpaRepository.findById(requestId).orElse(null) ?: return
		entity.softDelete(now)
		loungeChatRequestJpaRepository.save(entity)
	}
}
