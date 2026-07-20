package com.org.oneulsogae.core.lounge.command.application.port.out

import java.time.LocalDateTime

/** 등록 빈도 제한 판단용으로 최근 셀소 등록 건수를 세는 out-port. */
interface CountRecentSelfIntroPostPort {

	/** [userId]가 [since] 이후에 등록한 셀소 글 수. */
	fun countSelfIntroPostsCreatedAfter(userId: Long, since: LocalDateTime): Int
}
