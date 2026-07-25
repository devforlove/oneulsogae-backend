package com.org.oneulsogae.infra.lounge.command.adapter

import com.org.oneulsogae.core.lounge.command.application.port.out.DeleteLoungeCommentPort
import com.org.oneulsogae.core.lounge.command.application.port.out.GetLoungeCommentPort
import com.org.oneulsogae.core.lounge.command.application.port.out.SaveLoungeCommentPort
import com.org.oneulsogae.core.lounge.command.domain.LoungeComment
import com.org.oneulsogae.infra.lounge.command.entity.LoungeCommentEntity
import com.org.oneulsogae.infra.lounge.command.mapper.toDomain
import com.org.oneulsogae.infra.lounge.command.mapper.toEntity
import com.org.oneulsogae.infra.lounge.command.repository.LoungeCommentJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/** 라운지 댓글 엔티티의 out-port 어댑터. (엔티티당 어댑터 하나 — 조회·저장·삭제를 함께 구현) */
@Component
class LoungeCommentAdapter(
	private val loungeCommentJpaRepository: LoungeCommentJpaRepository,
) : GetLoungeCommentPort, SaveLoungeCommentPort, DeleteLoungeCommentPort {

	// @SQLRestriction이 없어 삭제 행도 deleted=true 도메인으로 반환된다. (삭제 여부 판정은 도메인 몫)
	override fun findById(commentId: Long): LoungeComment? =
		loungeCommentJpaRepository.findById(commentId).orElse(null)?.toDomain()

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(comment: LoungeComment): LoungeComment =
		loungeCommentJpaRepository.save(comment.toEntity()).toDomain()

	// 이미 삭제됐거나 없는 행이면 아무것도 하지 않는다.
	override fun delete(commentId: Long, now: LocalDateTime) {
		val entity: LoungeCommentEntity = loungeCommentJpaRepository.findById(commentId).orElse(null) ?: return
		if (entity.isDeleted) {
			return
		}
		entity.softDelete(now)
		loungeCommentJpaRepository.save(entity)
	}
}
