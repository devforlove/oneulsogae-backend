package com.org.oneulsogae.core.user.command.application.port.`in`

import com.org.oneulsogae.core.user.command.application.port.`in`.command.UpdateUserDetailCommand

/**
 * 온보딩 완료 인포트(유스케이스).
 * 온보딩 입력값(프로필 상세)을 저장하고 코인 잔액 행을 준비한 뒤, 정식 가입(ACTIVE)으로 전환한다.
 * 이번 호출로 막 가입이 완료되면 가입 축하 코인 지급·첫 매칭 추천·매칭 읽기 모델 동기화를 함께 처리한다.
 */
interface CompleteOnboardingUseCase {

	fun complete(userId: Long, command: UpdateUserDetailCommand, referralCode: String? = null)
}
