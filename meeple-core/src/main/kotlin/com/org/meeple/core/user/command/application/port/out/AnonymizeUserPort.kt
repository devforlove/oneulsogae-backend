package com.org.meeple.core.user.command.application.port.out

/** 파기: users 행의 개인정보를 익명화하는 아웃포트(소프트삭제 행 대상, 네이티브). */
interface AnonymizeUserPort {

	/** [userId]의 email=null, provider_id=[anonymizedProviderId], status=WITHDRAWN으로 익명화한다. */
	fun anonymize(userId: Long, anonymizedProviderId: String)
}
