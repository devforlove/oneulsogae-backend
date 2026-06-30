package com.org.meeple.core.user.command.application.port.out

/** 파기: users 행의 개인정보를 익명화하는 아웃포트(소프트삭제 행 대상, 네이티브). */
interface AnonymizeUserPort {

	/**
	 * [userId]의 email=null, provider_id=[anonymizedProviderId], status=WITHDRAWN으로 익명화한다.
	 * 실제 갱신이 일어났으면 true, 대상 행이 없거나 이미 복구된 경우(deleted_at=null) 또는
	 * 이미 WITHDRAWN이면 false를 반환한다.
	 */
	fun anonymize(userId: Long, anonymizedProviderId: String): Boolean
}
