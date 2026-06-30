package com.org.meeple.core.inquiry.command.domain

import com.org.meeple.common.inquiry.InquiryCategory
import com.org.meeple.common.inquiry.InquiryStatus
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.inquiry.InquiryErrorCode
import java.time.LocalDateTime

private const val MESSAGE_MIN_LENGTH: Int = 10
private const val MESSAGE_MAX_LENGTH: Int = 1000
private val EMAIL_REGEX: Regex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/**
 * 고객센터 1:1 문의 도메인 모델. (명령 측 — 생성/저장에 쓴다)
 * 접수 시각은 별도 필드 없이 영속성의 created_at(JPA Auditing)으로 갈음한다.
 * status/answer/answeredAt는 추후 운영자 답변용으로 선반영했고, 생성 시에는 PENDING·null이다.
 * 영속성은 [com.org.meeple.infra.inquiry.command.entity.InquiryEntity]가 담당한다.
 */
data class Inquiry(
	val id: Long = 0,
	val userId: Long?,
	val category: InquiryCategory,
	val email: String,
	val message: String,
	val status: InquiryStatus = InquiryStatus.PENDING,
	val answer: String? = null,
	val answeredAt: LocalDateTime? = null,
) {
	companion object {

		/** 작성자([userId], 비로그인이면 null)의 문의를 [category]·[email]·[message]로 접수한다. 입력을 검증한 뒤 PENDING으로 만든다. */
		fun create(
			userId: Long?,
			category: InquiryCategory,
			email: String,
			message: String,
		): Inquiry {
			validateInquiry(email, message)
			return Inquiry(
				userId = userId,
				category = category,
				email = email,
				message = message,
			)
		}

		private fun validateInquiry(email: String, message: String) {
			if (!EMAIL_REGEX.matches(email)) {
				throw BusinessException(InquiryErrorCode.INVALID_EMAIL)
			}
			if (message.length < MESSAGE_MIN_LENGTH) {
				throw BusinessException(InquiryErrorCode.MESSAGE_TOO_SHORT)
			}
			if (message.length > MESSAGE_MAX_LENGTH) {
				throw BusinessException(InquiryErrorCode.MESSAGE_TOO_LONG)
			}
		}
	}
}
