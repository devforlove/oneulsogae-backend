package com.org.meeple.infra.report.command.entity

import com.org.meeple.common.report.ReportType
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * 사용자 신고 엔티티. 신고자(from_user_id)가 대상(to_team_id 또는 to_user_id)을 사유(type)와 함께 신고한다.
 * 대상·채팅방(chat_room_id)·상세 설명(description)은 상황에 따라 없을 수 있어 nullable이다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(name = "reports")
class ReportEntity(
	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, columnDefinition = "varchar(50)")
	val type: ReportType,

	@Column(name = "from_user_id", nullable = false)
	val fromUserId: Long,

	@Column(name = "chat_room_id")
	val chatRoomId: Long? = null,

	@Column(name = "to_team_id")
	val toTeamId: Long? = null,

	@Column(name = "to_user_id")
	val toUserId: Long? = null,

	@Column(name = "description", length = 1000)
	val description: String? = null,
) : BaseEntity()
