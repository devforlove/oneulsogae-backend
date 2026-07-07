package com.org.meeple.admin.notice.command.application.port.`in`.command

/** 어드민 공지 생성 입력. (저장 날짜는 created_at으로 자동 기록) */
data class CreateAdminNoticeCommand(
	val title: String,
	val description: String,
)
