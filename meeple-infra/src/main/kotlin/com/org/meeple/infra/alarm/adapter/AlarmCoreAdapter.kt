package com.org.meeple.infra.alarm.adapter

import com.org.meeple.core.alarm.application.port.out.GetAlarmPort
import com.org.meeple.core.alarm.application.port.out.SaveAlarmPort
import com.org.meeple.core.alarm.domain.Alarm
import com.org.meeple.core.alarm.domain.Alarms
import com.org.meeple.infra.alarm.mapper.toDomain
import com.org.meeple.infra.alarm.mapper.toEntity
import com.org.meeple.infra.alarm.repository.AlarmJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 알람 아웃포트([SaveAlarmPort], [GetAlarmPort])의 JPA 구현 어댑터.
 * 엔티티/도메인 변환([AlarmMapper])을 책임지며, 외부에는 도메인 모델만 노출한다.
 */
@Component
class AlarmCoreAdapter(
	private val alarmJpaRepository: AlarmJpaRepository,
) : SaveAlarmPort, GetAlarmPort {

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(alarm: Alarm): Alarm =
		alarmJpaRepository.save(alarm.toEntity()).toDomain()

	override fun findByUserIdSince(userId: Long, since: LocalDateTime): Alarms =
		Alarms(
			alarmJpaRepository.findByUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDescIdDesc(userId, since)
				.map { it.toDomain() },
		)
}
