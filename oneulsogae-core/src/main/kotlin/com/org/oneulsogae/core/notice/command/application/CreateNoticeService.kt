package com.org.oneulsogae.core.notice.command.application

import com.org.oneulsogae.core.notice.command.application.port.`in`.CreateNoticeUseCase
import com.org.oneulsogae.core.notice.command.application.port.`in`.command.CreateNoticeCommand
import com.org.oneulsogae.core.notice.command.application.port.out.SaveNoticePort
import com.org.oneulsogae.core.notice.command.domain.Notice
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [CreateNoticeUseCase] 구현. [command]로 공지([Notice])를 만들어 저장한다.
 * 저장 날짜는 영속성의 created_at(JPA Auditing)으로 자동 기록되므로 여기서 다루지 않는다.
 */
@Service
@Transactional
class CreateNoticeService(
	private val saveNoticePort: SaveNoticePort,
) : CreateNoticeUseCase {

	override fun create(command: CreateNoticeCommand): Notice =
		saveNoticePort.save(Notice.create(title = command.title, description = command.description))
}
