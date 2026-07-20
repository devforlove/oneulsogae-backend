package com.org.oneulsogae.admin.inquiry.command.domain

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.common.inquiry.InquiryStatus
import java.time.LocalDateTime

/**
 * 어드민 문의 답변 도메인 모델(명령 측). 답변 가능 여부 판정에 필요한 id·status만 담는다.
 * (admin은 core에 의존하지 않으므로 core Inquiry를 쓰지 않고 자체 최소 모델을 둔다)
 */
data class AdminInquiry(
	val id: Long,
	val status: InquiryStatus,
) {
	/**
	 * 문의에 답변한다. PENDING이 아니면(이미 답변됨) INQUIRY_ALREADY_ANSWERED.
	 * 통과 시 저장할 답변 값([AnsweredInquiry])을 만든다. (재답변 불허 규칙을 도메인에 캡슐화)
	 */
	fun answer(content: String, now: LocalDateTime): AnsweredInquiry {
		if (status != InquiryStatus.PENDING) {
			throw AdminException(AdminErrorCode.INQUIRY_ALREADY_ANSWERED, "이미 답변된 문의입니다: $id")
		}
		return AnsweredInquiry(id = id, answer = content, answeredAt = now)
	}
}

/** 답변 저장 값. 상태는 저장 시 ANSWERED로 전이한다. */
data class AnsweredInquiry(
	val id: Long,
	val answer: String,
	val answeredAt: LocalDateTime,
)
