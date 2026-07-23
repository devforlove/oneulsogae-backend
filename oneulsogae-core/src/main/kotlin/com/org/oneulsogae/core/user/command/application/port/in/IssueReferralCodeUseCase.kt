package com.org.oneulsogae.core.user.command.application.port.`in`

/**
 * 추천 코드 발급 인포트(유스케이스).
 * 내 추천 코드를 반환하고, 아직 없으면 생성·저장 후 반환한다. (get-or-create, 멱등)
 */
interface IssueReferralCodeUseCase {

	fun issue(userId: Long): String
}
