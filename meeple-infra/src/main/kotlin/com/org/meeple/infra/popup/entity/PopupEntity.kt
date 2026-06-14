package com.org.meeple.infra.popup.entity

import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/**
 * 앱에 노출하는 팝업(공지/이벤트 등) 영속성 엔티티.
 * [exposed]가 true이고 노출 기간([exposedFrom], [exposedTo]) 안일 때 노출 대상이며, [displayOrder] 오름차순으로 보여준다.
 * `order`는 SQL 예약어라 컬럼명을 display_order로 둔다.
 * (exposed, display_order) 복합 인덱스로 "노출 중인 팝업을 순서대로" 조회를 커버한다.
 * 도메인 로직은 popup 도메인 모델에 정의한다. (엔티티는 상태만 보관한다)
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "popups",
	indexes = [
		Index(name = "idx_exposed_display_order", columnList = "exposed, display_order"),
	],
)
class PopupEntity(
	/** 팝업 제목. */
	@Column(name = "title", nullable = false, length = 200)
	var title: String,

	/** 팝업 본문/설명. */
	@Column(name = "description", nullable = false, length = 1000)
	var description: String,

	/** 노출 정렬 순서. 작을수록 먼저 보여준다. (order는 SQL 예약어라 컬럼명은 display_order) */
	@Column(name = "display_order", nullable = false)
	var displayOrder: Int,

	/** 팝업 이미지 URL. 없으면 null. */
	@Column(name = "image_url", length = 500)
	var imageUrl: String? = null,

	/** 팝업 클릭 시 이동할 링크 URL. 없으면 null. */
	@Column(name = "link_url", length = 500)
	var linkUrl: String? = null,

	/** 노출 여부. false면 기간과 무관하게 노출하지 않는다. */
	@Column(name = "exposed", nullable = false)
	var exposed: Boolean = true,

	/** 노출 시작 시각. null이면 시작 제한 없음. */
	@Column(name = "exposed_from")
	var exposedFrom: LocalDateTime? = null,

	/** 노출 종료 시각. null이면 종료 제한 없음. */
	@Column(name = "exposed_to")
	var exposedTo: LocalDateTime? = null,
) : BaseEntity()
