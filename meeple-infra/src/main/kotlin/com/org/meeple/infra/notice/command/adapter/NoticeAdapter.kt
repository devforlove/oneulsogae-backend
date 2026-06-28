package com.org.meeple.infra.notice.command.adapter

import com.org.meeple.core.notice.command.application.port.out.SaveNoticePort
import com.org.meeple.core.notice.command.domain.Notice
import com.org.meeple.infra.notice.command.mapper.toDomain
import com.org.meeple.infra.notice.command.mapper.toEntity
import com.org.meeple.infra.notice.command.repository.NoticeJpaRepository
import org.springframework.stereotype.Component

/**
 * [NoticeEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 공지 저장 out-port([SaveNoticePort])를 구현한다.
 * 공지 조회는 [com.org.meeple.infra.notice.query.GetNoticeDaoImpl]가 따로 담당한다.
 */
@Component
class NoticeAdapter(
	private val noticeJpaRepository: NoticeJpaRepository,
) : SaveNoticePort {

	override fun save(notice: Notice): Notice =
		noticeJpaRepository.save(notice.toEntity()).toDomain()
}
