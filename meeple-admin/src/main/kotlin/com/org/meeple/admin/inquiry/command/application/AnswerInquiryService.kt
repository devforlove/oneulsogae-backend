package com.org.meeple.admin.inquiry.command.application

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.common.time.TimeGenerator
import com.org.meeple.admin.inquiry.command.application.port.`in`.AnswerInquiryUseCase
import com.org.meeple.admin.inquiry.command.application.port.`in`.command.AnswerInquiryCommand
import com.org.meeple.admin.inquiry.command.application.port.out.AnswerAdminInquiryPort
import com.org.meeple.admin.inquiry.command.application.port.out.GetAdminInquiryPort
import com.org.meeple.admin.inquiry.command.domain.AdminInquiry
import com.org.meeple.admin.inquiry.command.domain.AnsweredInquiry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [AnswerInquiryUseCase] 구현. (명령)
 * 대상 문의를 로드해 없으면 INQUIRY_NOT_FOUND. 답변 가능 여부(PENDING)는 도메인이 판정하고,
 * 통과 시 답변 값을 저장(answer/answered_at + status=ANSWERED)한다. 답변 시각은 TimeGenerator로 얻는다.
 */
@Service
@Transactional
class AnswerInquiryService(
	private val getAdminInquiryPort: GetAdminInquiryPort,
	private val answerAdminInquiryPort: AnswerAdminInquiryPort,
	private val timeGenerator: TimeGenerator,
) : AnswerInquiryUseCase {

	override fun answer(command: AnswerInquiryCommand) {
		val inquiry: AdminInquiry = getAdminInquiryPort.findById(command.inquiryId)
			?: throw AdminException(AdminErrorCode.INQUIRY_NOT_FOUND, "문의를 찾을 수 없습니다: ${command.inquiryId}")
		val answered: AnsweredInquiry = inquiry.answer(command.answer, timeGenerator.now())
		answerAdminInquiryPort.answer(answered)
	}
}
