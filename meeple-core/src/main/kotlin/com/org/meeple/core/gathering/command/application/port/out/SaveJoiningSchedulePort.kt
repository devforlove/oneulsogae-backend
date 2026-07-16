package com.org.meeple.core.gathering.command.application.port.out

import com.org.meeple.core.gathering.command.domain.JoiningSchedule

/** 접수로 차감된 일정 여분(성별·얼리버드)을 반영하는 아웃포트. */
interface SaveJoiningSchedulePort {

	fun save(schedule: JoiningSchedule)
}
