package com.org.meeple.infra.gathering.command.repository

import com.org.meeple.infra.gathering.command.entity.GatheringScheduleEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GatheringScheduleJpaRepository : JpaRepository<GatheringScheduleEntity, Long> {

	/**
	 * 참가 접수의 여분 차감을 위해 비관적 쓰기 락으로 일정 행을 조회한다. (SELECT ... FOR UPDATE)
	 * 트랜잭션 커밋 전까지 행을 잠가 동시 접수의 차감을 직렬화한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from GatheringScheduleEntity s where s.id = :id")
	fun findByIdForUpdate(@Param("id") id: Long): GatheringScheduleEntity?
}
