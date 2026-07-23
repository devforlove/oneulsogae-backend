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
import java.time.LocalDateTime

/**
 * 라운지 셀소 대화 신청 영속성 엔티티.
 * 수락으로 생성된 채팅방은 컬럼으로 두지 않는다 — chat_rooms(match_type=LOUNGE, match_id=이 행의 id)로 역참조한다.
 * 삭제는 soft delete(deleted_at)로 처리한다.
 *
 * [receiverUserId]는 글 작성자([LoungePostEntity]의 user_id)를 비정규화한 값이다.
 * 받은/보낸 신청 목록이 모두 "한 사용자의 신청을 글과 무관하게 최신순으로" 훑는 조회라,
 * 조인 없이 (사용자, id desc)를 인덱스 하나로 seek + 정렬하려면 신청 행이 수신자를 알아야 한다.
 * 글 작성자는 바뀌지 않으므로 복사 저장해도 원본과 어긋나지 않는다.
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
		// 내가 받은 신청 목록(최신순). 동등 조건(receiver_user_id)과 정렬 컬럼(id desc)을 한 인덱스로 받친다.
		Index(name = "idx_receiver_user_id_id", columnList = "receiver_user_id, id"),
		// 내가 보낸 신청 목록(최신순). ux_post_requester는 선두가 post_id라 requester_user_id로 seek할 수 없어 따로 둔다.
		Index(name = "idx_requester_user_id_id", columnList = "requester_user_id, id"),
		// 만료 정리 배치의 "PENDING + expired_at < now" 조회를 seek로 받친다. (등치 컬럼 → 범위 컬럼 순)
		Index(name = "idx_status_expired_at", columnList = "status, expired_at"),
	],
)
class LoungeChatRequestEntity(
	/** 대상 셀소 글([LoungePostEntity])의 id. */
	@Column(name = "post_id", nullable = false)
	val postId: Long,

	/** 대화를 신청한 사용자. */
	@Column(name = "requester_user_id", nullable = false)
	val requesterUserId: Long,

	/** 신청을 받은 사용자(글 작성자). lounge_posts.user_id를 비정규화한 값이다. */
	@Column(name = "receiver_user_id", nullable = false)
	val receiverUserId: Long,

	/** 신청 상태. 수락되면 ACCEPTED로 바뀐다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	var status: LoungeChatRequestStatus = LoungeChatRequestStatus.PENDING,

	/** 만료 시각(신청 시각 + 3일). 이 시각이 지난 PENDING 신청은 수락 불가·목록 제외로 다룬다. */
	@Column(name = "expired_at", nullable = false)
	val expiredAt: LocalDateTime,
) : BaseEntity()
