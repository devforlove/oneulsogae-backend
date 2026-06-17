package com.org.meeple.infra.alarm.command.adapter

import com.org.meeple.core.alarm.command.application.port.out.SaveAlarmPort
import com.org.meeple.core.alarm.command.domain.Alarm
import com.org.meeple.infra.alarm.command.mapper.toDomain
import com.org.meeple.infra.alarm.command.mapper.toEntity
import com.org.meeple.infra.alarm.command.repository.AlarmJpaRepository
import org.springframework.stereotype.Component

/**
 * 알람 저장 아웃포트([SaveAlarmPort])의 JPA 구현 어댑터. (명령 경로)
 * 엔티티/도메인 변환([com.org.meeple.infra.alarm.command.mapper])을 책임지며, 외부에는 도메인 모델만 노출한다.
 * 조회는 [com.org.meeple.infra.alarm.query.GetAlarmDaoImpl]가 담당한다.
 */
@Component
class AlarmAdapter(
	private val alarmJpaRepository: AlarmJpaRepository,
) : SaveAlarmPort {

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(alarm: Alarm): Alarm =
		alarmJpaRepository.save(alarm.toEntity()).toDomain()
}
