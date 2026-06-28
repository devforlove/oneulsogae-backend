package com.org.meeple.core.notice.command.application.port.`in`.command

/** 공지 생성 입력. 제목([title])과 설명([description])을 담는다. (저장 날짜는 created_at으로 자동 기록) */
data class CreateNoticeCommand(
	val title: String,
	val description: String,
)
