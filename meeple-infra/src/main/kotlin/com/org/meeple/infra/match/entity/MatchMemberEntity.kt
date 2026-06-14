package com.org.meeple.infra.match.entity

import com.org.meeple.common.user.Gender
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction

/**
 * 매칭(소개)에 참가한 사용자 한 명의 참가 정보 영속성 엔티티.
 * 기존 [MatchEntity]는 참가자를 (male_user_id, female_user_id) 두 컬럼으로 비정규화해 1:1에 고정돼 있으나,
 * 이 엔티티는 (match_id, user_id) 한 쌍을 한 행으로 정규화해 1:1·N:N(2:2·3:3) 미팅으로 확장할 토대를 만든다.
 * (match_id, user_id) 유니크 제약으로 한 매칭에 같은 사용자가 중복 참가하는 것을 막고, user_id 단독 인덱스로 사용자별 참가 매칭 조회를 커버한다.
 * 현재는 확장 씨앗 단계로, 매칭 생성 시 함께 기록만 한다. (수락·조회·배치 로직은 아직 MatchEntity의 male/female 기준)
 * 도메인 로직은 [com.org.meeple.core.match.domain.MatchMember] 모델에 정의한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "match_members",
	uniqueConstraints = [
		UniqueConstraint(name = "udx_match_id_user_id", columnNames = ["match_id", "user_id"]),
	],
	indexes = [
		Index(name = "idx_match_members_user_id", columnList = "user_id"),
	],
)
class MatchMemberEntity(
	@Column(name = "match_id", nullable = false)
	val matchId: Long,

	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** 참가자 성별. 성별 균형 구성·성별 기반 조회에 쓴다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "gender", nullable = false, length = 10)
	val gender: Gender,
) : BaseEntity()
