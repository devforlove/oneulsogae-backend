package com.org.oneulsogae.core.gathering.command.application.port.out

import com.org.oneulsogae.core.gathering.command.domain.JoiningSchedule

/** 참가 접수 대상 일정을 비관적 쓰기 락으로 조회하는 아웃포트. (동시 접수의 여분 차감 직렬화) */
interface GetJoiningSchedulePort {

	fun getForUpdate(scheduleId: Long): JoiningSchedule?
}
