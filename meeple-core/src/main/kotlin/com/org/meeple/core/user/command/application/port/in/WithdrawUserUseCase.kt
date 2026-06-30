package com.org.meeple.core.user.command.application.port.`in`

/** 회원 탈퇴 유스케이스. 계정을 비활성(소프트삭제)하고 토큰 폐기·매칭 풀 제거를 수행한다. (데이터는 보존, 10일 내 복구 가능) */
interface WithdrawUserUseCase {

	fun withdraw(userId: Long)
}
