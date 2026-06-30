package com.org.meeple.infra.notification.command.entity

import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * notification_preferences 테이블. 사용자당 1행(user_id UNIQUE)으로 알림 설정을 보관한다.
 * 도메인 로직을 두지 않고 상태만 보관한다. 소프트 삭제는 쓰지 않는다.
 */
@Entity
@Table(
	name = "notification_preferences",
	// 단건 조회/upsert가 user_id 등치로 인덱스 seek. 유저당 1행을 강제한다.
	indexes = [Index(name = "ux_user_id", columnList = "user_id", unique = true)],
)
class NotificationPreferenceEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** 푸시 알림 마스터 스위치. 끄면 모든 알림톡 차단. */
	@Column(name = "push", nullable = false)
	var push: Boolean,

	/** 소개팅(1:1) 관심·매칭 알림. */
	@Column(name = "one_to_one", nullable = false)
	var oneToOne: Boolean,

	/** 미팅(다대다) 관심·매칭 알림. */
	@Column(name = "meeting", nullable = false)
	var meeting: Boolean,

	/** 팀 초대·수락·해체 알림. */
	@Column(name = "team", nullable = false)
	var team: Boolean,

	/** 새 메시지 알림. (현재 대응 AlarmType 없음 — 예약) */
	@Column(name = "message", nullable = false)
	var message: Boolean,

	/** 마케팅·이벤트 알림. (현재 대응 AlarmType 없음 — 예약) */
	@Column(name = "marketing", nullable = false)
	var marketing: Boolean,
) : BaseEntity()
