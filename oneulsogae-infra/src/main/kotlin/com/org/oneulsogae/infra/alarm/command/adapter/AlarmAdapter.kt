package com.org.oneulsogae.infra.alarm.command.adapter

import com.org.oneulsogae.core.alarm.command.application.port.out.MarkAlarmsReadPort
import com.org.oneulsogae.core.alarm.command.application.port.out.SaveAlarmPort
import com.org.oneulsogae.core.alarm.command.domain.Alarm
import com.org.oneulsogae.infra.alarm.command.mapper.toDomain
import com.org.oneulsogae.infra.alarm.command.mapper.toEntity
import com.org.oneulsogae.infra.alarm.command.repository.AlarmJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 알람 명령 아웃포트([SaveAlarmPort]·[MarkAlarmsReadPort])의 JPA 구현 어댑터. (한 엔티티 - 한 어댑터)
 * 엔티티/도메인 변환([com.org.oneulsogae.infra.alarm.command.mapper])을 책임지며, 외부에는 도메인 모델만 노출한다.
 * 조회는 [com.org.oneulsogae.infra.alarm.query.GetAlarmDaoImpl]가 담당한다.
 */
@Component
class AlarmAdapter(
	private val alarmJpaRepository: AlarmJpaRepository,
) : SaveAlarmPort, MarkAlarmsReadPort {

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(alarm: Alarm): Alarm =
		alarmJpaRepository.save(alarm.toEntity()).toDomain()

	override fun markAllReadByUserIdSince(userId: Long, since: LocalDateTime): Int =
		alarmJpaRepository.markAllReadByUserIdSince(userId, since)
}
