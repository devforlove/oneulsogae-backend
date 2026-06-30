package com.org.meeple.infra.alarm.command.repository

import com.org.meeple.common.alarm.AlarmType
import com.org.meeple.infra.alarm.command.entity.AlarmEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/**
 * 알람 영속성 엔티티에 대한 Spring Data JPA 리포지토리. (명령 경로 - 저장·일괄 읽음 처리)
 * 저장 out-port는 [com.org.meeple.infra.alarm.command.adapter.AlarmAdapter]가 구현하고,
 * 조회는 [com.org.meeple.infra.alarm.query.GetAlarmDaoImpl]가 QueryDSL로 담당한다.
 */
interface AlarmJpaRepository : JpaRepository<AlarmEntity, Long> {

	/**
	 * [since](포함) 이후 생성된 사용자의 읽지 않은 알람을 모두 읽음 처리하고, 영향 행 수를 반환한다.
	 * 이미 읽은 알람은 갱신하지 않아(is_read = false 조건) 불필요한 쓰기를 피한다.
	 * 벌크 update는 영속성 컨텍스트를 우회하므로 clearAutomatically로 1차 캐시를 비워 stale 엔티티를 막는다.
	 */
	@Modifying(clearAutomatically = true)
	@Query(
		"update AlarmEntity a set a.isRead = true " +
			"where a.userId = :userId and a.isRead = false and a.createdAt >= :since",
	)
	fun markAllReadByUserIdSince(@Param("userId") userId: Long, @Param("since") since: LocalDateTime): Int

	/**
	 * [userIds] 중 [since](포함) 이후 [type] 알람을 이미 받은 userId만 추려 반환한다. (당일 중복 알림 차단용)
	 * 복합 인덱스(user_id, created_at)로 userId IN seek + created_at 범위를 받쳐, type은 잔여 필터로 본다.
	 */
	@Query(
		"select distinct a.userId from AlarmEntity a " +
			"where a.userId in :userIds and a.type = :type and a.createdAt >= :since",
	)
	fun findAlarmedUserIds(
		@Param("userIds") userIds: Collection<Long>,
		@Param("type") type: AlarmType,
		@Param("since") since: LocalDateTime,
	): List<Long>
}
