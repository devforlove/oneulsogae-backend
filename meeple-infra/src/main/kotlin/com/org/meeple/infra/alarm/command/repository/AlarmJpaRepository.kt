package com.org.meeple.infra.alarm.command.repository

import com.org.meeple.infra.alarm.command.entity.AlarmEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 알람 영속성 엔티티에 대한 Spring Data JPA 리포지토리. (명령 경로 - 저장)
 * 저장 out-port는 [com.org.meeple.infra.alarm.command.adapter.AlarmAdapter]가 구현하고,
 * 조회는 [com.org.meeple.infra.alarm.query.GetAlarmDaoImpl]가 QueryDSL로 담당한다.
 */
interface AlarmJpaRepository : JpaRepository<AlarmEntity, Long>
