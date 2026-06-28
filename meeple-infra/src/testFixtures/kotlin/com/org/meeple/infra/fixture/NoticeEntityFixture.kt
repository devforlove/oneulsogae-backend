package com.org.meeple.infra.fixture

import com.org.meeple.infra.notice.command.entity.NoticeEntity

/**
 * [NoticeEntity] 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다.
 * 저장 날짜(created_at)는 저장 시 JPA Auditing이 채운다.
 */
object NoticeEntityFixture {

	fun create(
		title: String = "공지 제목",
		description: String = "공지 설명",
	): NoticeEntity =
		NoticeEntity(
			title = title,
			description = description,
		)
}
