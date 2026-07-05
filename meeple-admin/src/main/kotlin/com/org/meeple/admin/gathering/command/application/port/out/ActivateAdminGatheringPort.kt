package com.org.meeple.admin.gathering.command.application.port.out

/** 모임 활성화 저장 out-port. status를 RECRUITING(모집중)으로 전이한다. infra 어댑터가 구현한다. */
fun interface ActivateAdminGatheringPort {
	fun activate(id: Long)
}
