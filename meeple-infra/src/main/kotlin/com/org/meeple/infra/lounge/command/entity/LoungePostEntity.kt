package com.org.meeple.infra.lounge.command.entity

import com.org.meeple.common.lounge.LoungePostType
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * 라운지에 올라간 글 한 건의 공통 골격을 담는 영속성 엔티티. 타입별 본문은 별도 테이블이 1:1로 보관한다.
 * (셀소 → [SelfIntroPostEntity], 사진 → [LoungePostImageEntity], 좋아요 → [LoungePostLikeEntity])
 * [likeCount]는 그리드 목록에서 매번 집계 조인을 걸지 않으려고 두는 비정규화 카운트다.
 * 삭제는 soft delete(deleted_at)로 처리한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "lounge_posts",
	indexes = [
		// 타입별 피드 목록(최신순) 조회용. 동등 조건(type)과 정렬 컬럼(id desc)을 한 인덱스로 받친다.
		Index(name = "idx_type_id", columnList = "type, id"),
		// 내가 쓴 글 조회용.
		Index(name = "idx_user_id", columnList = "user_id"),
	],
)
class LoungePostEntity(
	/** 글 종류. */
	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, columnDefinition = "varchar(50)")
	var type: LoungePostType,

	/** 작성자. */
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** 좋아요 수(비정규화). 실제 좋아요 행은 [LoungePostLikeEntity]가 보관한다. */
	@Column(name = "like_count", nullable = false)
	var likeCount: Int = 0,
) : BaseEntity()
