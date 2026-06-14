package com.org.meeple.infra.alarm.entity

import com.org.meeple.common.alarm.AlarmType
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * alarms 테이블 영속성 엔티티. 사용자별 알람(알림)이 한 건씩 쌓인다.
 * 도메인 로직을 두지 않고 상태만 보관한다. 생성 시각은 BaseEntity가 보관한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "alarms",
	// 내 알람 목록 조회용. (user_id 등치 + created_at 범위(최근 1개월) seek + created_at 정렬을 filesort 없이 충족)
	// InnoDB 보조 인덱스에 PK(id)가 암묵 포함돼 created_at 동률 시 id 정렬까지 인덱스로 커버한다.
	indexes = [Index(name = "idx_alarms_user_id_created_at", columnList = "user_id, created_at")],
)
class AlarmEntity(
	/** 알람 수신자 id. */
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** 알람 유형. */
	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 30)
	val type: AlarmType,

	/** 노출 제목. */
	@Column(name = "title", nullable = false, length = 100)
	val title: String,

	/** 노출 본문. */
	@Column(name = "description", nullable = false, length = 1000)
	val description: String,

	/** 알람을 눌렀을 때 이동할 대상 경로. */
	@Column(name = "link", nullable = false, length = 500)
	val link: String,

	/** 알람을 유발한 상대 사용자 id. (없을 수 있음) */
	@Column(name = "from_user_id")
	val fromUserId: Long? = null,

	/** 읽음 여부. */
	@Column(name = "is_read", nullable = false)
	var isRead: Boolean = false,
) : BaseEntity()
