package com.org.meeple.infra.image.entity

import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction

/**
 * image_templates 테이블 영속성 엔티티. 배포 없이 교체 가능한 이미지(URL·치수)를 [code]로 식별하는 참조 데이터다.
 * 팝업 등 여러 도메인이 [code]로 참조해 조회 시점에 현재 이미지를 해석한다. (앱은 읽기만, 행은 DB에서 관리)
 * 도메인 로직을 두지 않고 상태만 보관한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "image_templates",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_image_template_code", columnNames = ["code"]),
	],
)
class ImageTemplateEntity(
	/** 소비처가 참조하는 고유 코드. 예: "POPUP_MATCH_FAILED_REFUND". */
	@Column(name = "code", nullable = false, length = 100)
	var code: String,

	/** 이미지 URL. */
	@Column(name = "image_url", nullable = false, length = 500)
	var imageUrl: String,

	/** 이미지 너비(px). */
	@Column(name = "image_width", nullable = false)
	var imageWidth: Int,

	/** 이미지 높이(px). */
	@Column(name = "image_height", nullable = false)
	var imageHeight: Int,
) : BaseEntity()
