package com.org.meeple.admin.notice.command.domain

/**
 * 어드민이 등록하는 공지 도메인 모델(명령 측). 제목·설명만 담는다.
 * 저장 날짜는 별도 필드 없이 영속성의 created_at(JPA Auditing)으로 갈음한다.
 * (admin은 core에 의존하지 않으므로 core Notice를 쓰지 않고 자체 최소 모델을 둔다)
 * 입력 검증은 요청 DTO(CreateAdminNoticeRequest)에서 처리한다.
 */
data class AdminNotice(
	val id: Long = 0,
	val title: String,
	val description: String,
) {
	companion object {
		fun create(title: String, description: String): AdminNotice =
			AdminNotice(title = title, description = description)
	}
}
