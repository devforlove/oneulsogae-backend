package com.org.meeple.infra.gathering.command.entity

import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * 모임의 정체성을 담는 영속성 엔티티. 참가자는 [GatheringMemberEntity]가 1:N으로 보관한다. (gathering_id로 연결)
 * [userId]는 모임을 만든 생성자다. **null이면 운영(관리자)이 만든 모임, 값이 있으면 해당 유저가 만든 모임**이다.
 * (일반 유저도 모임을 만들 수 있도록 확장을 고려한 설계 — 지금은 운영이 만들고 추후 유저 생성을 연다)
 * 주최자는 [userId]로만 표현하며 [GatheringMemberEntity]는 순수 참가자만 보관한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "gatherings",
	indexes = [
		// 상태·종류로 필터하는 모임 목록 조회용. 동등 조건(status, type)을 인덱스로 받친다.
		Index(name = "idx_status_type", columnList = "status, type"),
		// 내가 만든 모임 조회용.
		Index(name = "idx_user_id", columnList = "user_id"),
	],
)
class GatheringEntity(
	/** 모임 종류. */
	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, columnDefinition = "varchar(50)")
	var type: GatheringType,

	/** 모임 생성자. null이면 운영(관리자) 생성, 값이 있으면 해당 유저 생성. */
	@Column(name = "user_id")
	var userId: Long? = null,

	/** 모임 제목. */
	@Column(name = "title", nullable = false, length = 100)
	var title: String,

	/** 모임 소개. */
	@Column(name = "description", length = 1000)
	var description: String? = null,

	/** 모임 대표 이미지의 S3 오브젝트 키. 없으면 null. (열람용 URL은 조회 시 presigned로 발급) */
	@Column(name = "image_key", length = 512)
	var imageKey: String? = null,

	/** 모임 활동지역(자유 텍스트). 예: "서울 강남구". */
	@Column(name = "region", nullable = false, length = 100)
	var region: String,

	/** 최소 참가 인원(모임 성사 기준). */
	@Column(name = "min_participants", nullable = false)
	var minParticipants: Int,

	/** 최대 참가 인원(정원). */
	@Column(name = "max_participants", nullable = false)
	var maxParticipants: Int,

	/** 모임 진행 상태. 모집중으로 시작한다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	var status: GatheringStatus = GatheringStatus.RECRUITING,
) : BaseEntity()
