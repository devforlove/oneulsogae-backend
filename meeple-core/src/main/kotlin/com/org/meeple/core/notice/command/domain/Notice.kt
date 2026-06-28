package com.org.meeple.core.notice.command.domain

/**
 * 앱에 노출하는 공지사항 도메인 모델. (명령 측 — 생성/저장에 쓴다)
 * 저장 날짜는 별도 필드 없이 영속성의 created_at(생성 시각, JPA Auditing)으로 갈음한다.
 * 조회는 도메인 모델 대신 query 측 read model([com.org.meeple.core.notice.query.dto.NoticeView])을 쓴다.
 * 영속성은 [com.org.meeple.infra.notice.command.entity.NoticeEntity]가 담당한다.
 */
data class Notice(
	val id: Long = 0,
	val title: String,
	val description: String,
) {
	companion object {
		/** [title] 제목과 [description] 설명으로 신규 공지를 만든다. */
		fun create(title: String, description: String): Notice =
			Notice(title = title, description = description)
	}
}
