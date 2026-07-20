package com.org.oneulsogae.scheduler.user.command.application.port.out

/** 사용자 1명을 파기(익명화)하는 아웃포트. 구현은 infra 브리지가 core 유스케이스에 위임한다. */
interface PurgeWithdrawnUserPort {

	fun purge(userId: Long)
}
