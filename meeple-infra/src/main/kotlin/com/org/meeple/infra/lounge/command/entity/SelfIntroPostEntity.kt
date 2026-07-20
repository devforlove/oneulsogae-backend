package com.org.meeple.infra.lounge.command.entity

import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction

/**
 * 셀프 소개팅(셀소) 글의 본문을 담는 영속성 엔티티. [LoungePostEntity]와 post_id로 1:1이다.
 * 작성자의 성별·나이·키·지역·직업은 프로필(user 도메인)이 소유하므로 여기에 복사 저장하지 않고 조회 시 조인한다.
 * 본문 항목은 작성 화면에서 비워둘 수 있어 전부 nullable이며, 필수 여부는 애플리케이션 레이어에서 판단한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "self_intro_posts",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_post_id", columnNames = ["post_id"]),
	],
)
class SelfIntroPostEntity(
	/** 소속 라운지 글([LoungePostEntity])의 id. */
	@Column(name = "post_id", nullable = false)
	val postId: Long,

	/** 장거리 연애 가능 여부에 대한 답변. */
	@Column(name = "long_distance", length = 40)
	var longDistance: String? = null,

	/** 원하는 상대 나이대. */
	@Column(name = "desired_age", length = 40)
	var desiredAge: String? = null,

	/** 본인 MBTI. */
	@Column(name = "mbti", length = 10)
	var mbti: String? = null,

	/** 결혼에 대한 생각. */
	@Column(name = "marriage_thought", length = 500)
	var marriageThought: String? = null,

	/** 선호하는 상대의 성격·가치관. */
	@Column(name = "preferred_partner", length = 500)
	var preferredPartner: String? = null,

	/** 나의 매력 어필. */
	@Column(name = "charm_point", length = 500)
	var charmPoint: String? = null,

	/** 자유 한마디. */
	@Column(name = "free_word", length = 500)
	var freeWord: String? = null,
) : BaseEntity()
