package com.org.meeple.admin.notice.command.application

import com.org.meeple.admin.notice.command.application.port.`in`.CreateAdminNoticeUseCase
import com.org.meeple.admin.notice.command.application.port.`in`.command.CreateAdminNoticeCommand
import com.org.meeple.admin.notice.command.application.port.out.SaveAdminNoticePort
import com.org.meeple.admin.notice.command.domain.AdminNotice
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [CreateAdminNoticeUseCase] 구현. [command]로 공지([AdminNotice])를 만들어 저장한다.
 * 저장 날짜는 영속성의 created_at(JPA Auditing)으로 자동 기록된다.
 */
@Service
@Transactional
class CreateAdminNoticeService(
	private val saveAdminNoticePort: SaveAdminNoticePort,
) : CreateAdminNoticeUseCase {

	override fun create(command: CreateAdminNoticeCommand) {
		saveAdminNoticePort.save(AdminNotice.create(title = command.title, description = command.description))
	}
}
