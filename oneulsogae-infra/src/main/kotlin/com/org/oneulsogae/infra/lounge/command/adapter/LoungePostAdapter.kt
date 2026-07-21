package com.org.oneulsogae.infra.lounge.command.adapter

import com.org.oneulsogae.common.lounge.LoungePostType
import com.org.oneulsogae.core.lounge.command.application.port.out.CountRecentSelfIntroPostPort
import com.org.oneulsogae.core.lounge.command.application.port.out.GetLoungePostPort
import com.org.oneulsogae.core.lounge.command.application.port.out.SaveLoungePostPort
import com.org.oneulsogae.core.lounge.command.domain.LoungePost
import com.org.oneulsogae.infra.lounge.command.mapper.toDomain
import com.org.oneulsogae.infra.lounge.command.mapper.toEntity
import com.org.oneulsogae.infra.lounge.command.repository.LoungePostJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 라운지 글(공통 골격) 엔티티의 out-port 어댑터. (엔티티당 어댑터 하나)
 * 저장([SaveLoungePostPort])과 등록 빈도 판단용 카운트([CountRecentSelfIntroPostPort]),
 * 대화 신청에서 쓰는 단건 조회([GetLoungePostPort])를 함께 구현한다.
 */
@Component
class LoungePostAdapter(
	private val loungePostJpaRepository: LoungePostJpaRepository,
) : SaveLoungePostPort, CountRecentSelfIntroPostPort, GetLoungePostPort {

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(post: LoungePost): LoungePost =
		loungePostJpaRepository.save(post.toEntity()).toDomain()

	override fun countSelfIntroPostsCreatedAfter(userId: Long, since: LocalDateTime): Int =
		loungePostJpaRepository.countByUserIdAndTypeAndCreatedAtAfter(userId, LoungePostType.SELF_INTRO, since)

	// @SQLRestriction("deleted_at is null")이 걸려 있어 소프트 삭제된 글은 조회되지 않는다.
	override fun findById(postId: Long): LoungePost? =
		loungePostJpaRepository.findById(postId).orElse(null)?.toDomain()
}
