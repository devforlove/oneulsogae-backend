package com.org.oneulsogae.infra.lounge.command.entity

import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * 라운지 글([LoungePostEntity])에 올린 사진 한 장. 한 글이 여러 장을 가질 수 있어 1:N으로 분리한다.
 * 파일 자체는 S3에 두고 여기엔 오브젝트 키([imageKey])만 보관한다. (열람용 URL은 조회 시 presigned로 발급)
 * 갤러리 노출 순서는 [displayOrder] 오름차순이다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "lounge_post_images",
	indexes = [
		// 글 상세에서 사진을 순서대로 읽는 경로. 동등 조건(post_id) + 정렬 컬럼(display_order)을 함께 받친다.
		Index(name = "idx_post_id_display_order", columnList = "post_id, display_order"),
	],
)
class LoungePostImageEntity(
	/** 소속 라운지 글([LoungePostEntity])의 id. */
	@Column(name = "post_id", nullable = false)
	val postId: Long,

	/** 사진의 S3 오브젝트 키. */
	@Column(name = "image_key", nullable = false, length = 512)
	var imageKey: String,

	/** 갤러리 노출 순서(오름차순). */
	@Column(name = "display_order", nullable = false)
	var displayOrder: Int,
) : BaseEntity()
