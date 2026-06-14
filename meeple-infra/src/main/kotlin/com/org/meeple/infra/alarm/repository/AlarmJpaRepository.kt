package com.org.meeple.infra.alarm.repository

import com.org.meeple.infra.alarm.entity.AlarmEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

/**
 * 알람 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 [com.org.meeple.infra.alarm.adapter.AlarmCoreAdapter]가 구현한다.
 */
interface AlarmJpaRepository : JpaRepository<AlarmEntity, Long> {

	/**
	 * 사용자의 [createdAt](포함) 이후 알람을 생성 시각 내림차순(동률이면 id 내림차순)으로 조회한다.
	 * (user_id, created_at) 복합 인덱스로 등치+범위+정렬을 filesort 없이 충족한다. (id 정렬은 인덱스의 PK 부록으로 커버)
	 */
	fun findByUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDescIdDesc(
		userId: Long,
		createdAt: LocalDateTime,
	): List<AlarmEntity>
}
