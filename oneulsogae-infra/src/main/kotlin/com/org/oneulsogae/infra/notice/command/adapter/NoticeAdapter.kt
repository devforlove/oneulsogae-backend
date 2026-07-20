package com.org.oneulsogae.infra.notice.command.adapter

import com.org.oneulsogae.admin.notice.command.application.port.out.SaveAdminNoticePort
import com.org.oneulsogae.admin.notice.command.domain.AdminNotice
import com.org.oneulsogae.core.notice.command.application.port.out.SaveNoticePort
import com.org.oneulsogae.core.notice.command.domain.Notice
import com.org.oneulsogae.infra.notice.command.mapper.toDomain
import com.org.oneulsogae.infra.notice.command.mapper.toEntity
import com.org.oneulsogae.infra.notice.command.repository.NoticeJpaRepository
import org.springframework.stereotype.Component

/**
 * [NoticeEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 유저용 저장 out-port([SaveNoticePort])와 어드민 저장 out-port([SaveAdminNoticePort])를 함께 구현한다.
 * 공지 조회는 [com.org.oneulsogae.infra.notice.query.GetNoticeDaoImpl]·[com.org.oneulsogae.infra.notice.query.GetAdminNoticeDaoImpl]가 따로 담당한다.
 */
@Component
class NoticeAdapter(
	private val noticeJpaRepository: NoticeJpaRepository,
) : SaveNoticePort, SaveAdminNoticePort {

	override fun save(notice: Notice): Notice =
		noticeJpaRepository.save(notice.toEntity()).toDomain()

	override fun save(notice: AdminNotice) {
		noticeJpaRepository.save(notice.toEntity())
	}
}
