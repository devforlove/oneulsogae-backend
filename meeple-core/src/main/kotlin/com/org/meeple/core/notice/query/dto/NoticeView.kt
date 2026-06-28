package com.org.meeple.core.notice.query.dto

import java.time.LocalDateTime

/**
 * 공지 목록 조회 결과 한 건을 담는 읽기 모델(read model).
 * 커맨드 도메인([com.org.meeple.core.notice.command.domain.Notice])과 분리해, 조회 응답에 필요한 형태만 노출한다.
 * [createdAt]은 저장 날짜(생성 시각)다.
 */
data class NoticeView(
	val id: Long,
	val title: String,
	val description: String,
	val createdAt: LocalDateTime?,
)
