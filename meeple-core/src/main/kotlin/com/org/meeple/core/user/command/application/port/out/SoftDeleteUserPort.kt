package com.org.meeple.core.user.command.application.port.out

import java.time.LocalDateTime

/** 사용자 계정을 소프트삭제(비활성)하는 아웃포트. 탈퇴 유예의 시작점이며 데이터는 보존된다. */
interface SoftDeleteUserPort {

	/** [userId] 사용자의 users 행 deleted_at을 [at]으로 설정한다. (이미 삭제된 행은 변경 없음) */
	fun softDelete(userId: Long, at: LocalDateTime)
}
