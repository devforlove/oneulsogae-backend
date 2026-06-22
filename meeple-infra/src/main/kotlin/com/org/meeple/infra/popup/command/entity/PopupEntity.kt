package com.org.meeple.infra.popup.command.entity

import com.org.meeple.common.popup.PopupType
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/**
 * 앱에 노출하는 팝업(공지/이벤트 등) 영속성 엔티티.
 * [exposed]가 true이고 노출 기간([exposedFrom], [exposedTo]) 안일 때 노출 대상이며, [displayOrder] 오름차순으로 보여준다.
 * `order`는 SQL 예약어라 컬럼명을 display_order로 둔다.
 * 노출 끄기(숨김)는 별도 플래그 없이 soft delete(deleted_at)로 처리한다. (@SQLRestriction으로 삭제 행은 조회에서 제외)
 * [userId]가 null이면 전역 팝업, 값이 있으면 그 사용자에게만 노출하는 개인 팝업이다.
 * (user_id, exposed_from, exposed_to) 복합 인덱스로 노출 팝업 조회(user_id 등치/IS NULL + 기간 범위 필터)를 커버한다.
 * (정렬(display_order)은 DB가 아니라 read model이 하므로 인덱스에 두지 않는다)
 * 도메인 로직은 popup command 도메인 모델에 정의한다. (엔티티는 상태만 보관한다)
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "popups",
	indexes = [
		Index(name = "idx_user_id_exposed_from_exposed_to", columnList = "user_id, exposed_from, exposed_to"),
	],
)
class PopupEntity(
	/** 팝업 제목. 없으면 null. */
	@Column(name = "title", length = 200)
	var title: String? = null,

	/** 팝업 본문/설명. 없으면 null. */
	@Column(name = "description", length = 1000)
	var description: String? = null,

	/** 노출 정렬 순서. 작을수록 먼저 보여준다. (order는 SQL 예약어라 컬럼명은 display_order) */
	@Column(name = "display_order", nullable = false)
	var displayOrder: Int,

	/** 팝업 이미지 URL. 없으면 null. */
	@Column(name = "image_url", length = 500)
	var imageUrl: String? = null,

	/** 팝업 이미지 너비(px). 없으면 null. */
	@Column(name = "image_width")
	var imageWidth: Int? = null,

	/** 팝업 이미지 높이(px). 없으면 null. */
	@Column(name = "image_height")
	var imageHeight: Int? = null,

	/** 팝업 클릭 시 이동할 링크 URL. 없으면 null. */
	@Column(name = "link_url", length = 500)
	var linkUrl: String? = null,

	/** 팝업 버튼에 표시할 문구. 없으면 null. */
	@Column(name = "button_text", length = 100)
	var buttonText: String? = null,

	/** 팝업 유형. (일반/출석 보상 등) */
	@Enumerated(EnumType.STRING)
	@Column(name = "popup_type", nullable = false, columnDefinition = "varchar(50)")
	var popUpType: PopupType = PopupType.NORMAL,

	/** 개인 팝업 대상 사용자 id. null이면 전역 팝업(모든 사용자 노출). */
	@Column(name = "user_id")
	var userId: Long? = null,

	/** 노출 시작 시각. 시작 제한이 없으면 아주 과거의 센티넬 값으로 둔다. (nullable 대신 순수 레인지 비교용) */
	@Column(name = "exposed_from", nullable = false)
	var exposedFrom: LocalDateTime,

	/** 노출 종료 시각. 종료 제한이 없으면 아주 미래의 센티넬 값으로 둔다. */
	@Column(name = "exposed_to", nullable = false)
	var exposedTo: LocalDateTime,
) : BaseEntity()
