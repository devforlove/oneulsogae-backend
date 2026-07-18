package com.org.meeple.infra.gathering.command.entity

import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate

/**
 * 유저의 모임 프로필. 멤버 인증 승인 시 어드민이 확정한 직종·직장 상세와, 생일·키(user_details에서 가져옴)를 보관한다.
 * 유저당 1건이며(재승인 시 갱신), 나이는 생일로부터 조회 시점에 계산한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "gathering_profile",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_user_id", columnNames = ["user_id"]),
	],
)
class GatheringProfileEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** 직종. (멤버 인증 승인 시 어드민이 확정) */
	@Column(name = "job_category", nullable = false, length = 30)
	var jobCategory: String,

	/** 직장명/직종/직급 상세. (멤버 인증 승인 시 어드민이 확정) */
	@Column(name = "job_detail", nullable = false, length = 100)
	var jobDetail: String,

	/** 생일. 나이는 조회 시점에 계산한다. 생일 미상이면 null. */
	@Column(name = "birthday")
	var birthday: LocalDate? = null,

	/** 키(cm). user_details에서 가져온 값. 없으면 null. */
	@Column(name = "height")
	var height: Int? = null,
) : BaseEntity()
