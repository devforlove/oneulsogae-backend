package com.org.oneulsogae.infra.lounge.command.entity

import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction

/**
 * 라운지 셀소 대화 신청 영속성 엔티티.
 * 글 작성자(수신자)는 [LoungePostEntity]의 user_id가 단일 진실원천이라 여기에 복사 저장하지 않는다.
 * 수락으로 생성된 채팅방도 컬럼으로 두지 않는다 — chat_rooms(match_type=LOUNGE, match_id=이 행의 id)로 역참조한다.
 * 삭제는 soft delete(deleted_at)로 처리한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "lounge_chat_requests",
	// 같은 글에 같은 사용자가 두 번 신청하지 못하게 막는 최종 방어선. (분산 락을 뚫고 들어온 동시 요청 대비)
	uniqueConstraints = [
		UniqueConstraint(name = "ux_post_requester", columnNames = ["post_id", "requester_user_id"]),
	],
	indexes = [
		// 글별 신청 목록(최신순) 조회용. 동등 조건(post_id)과 정렬 컬럼(id desc)을 한 인덱스로 받친다.
		Index(name = "idx_post_id_id", columnList = "post_id, id"),
	],
)
class LoungeChatRequestEntity(
	/** 대상 셀소 글([LoungePostEntity])의 id. */
	@Column(name = "post_id", nullable = false)
	val postId: Long,

	/** 대화를 신청한 사용자. */
	@Column(name = "requester_user_id", nullable = false)
	val requesterUserId: Long,

	/** 신청 상태. 수락되면 ACCEPTED로 바뀐다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	var status: LoungeChatRequestStatus = LoungeChatRequestStatus.PENDING,
) : BaseEntity()
