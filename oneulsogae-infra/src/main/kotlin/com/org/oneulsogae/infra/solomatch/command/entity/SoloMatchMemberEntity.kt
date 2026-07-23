package com.org.oneulsogae.infra.solomatch.command.entity

import com.org.oneulsogae.common.match.MatchMemberStatus
import com.org.oneulsogae.common.user.Gender
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
 * 매칭(소개)에 참가한 사용자 한 명의 참가 정보 영속성 엔티티.
 * 참가자를 (match_id, user_id) 한 쌍의 행으로 정규화해, 참가자·수락의 단일 진실원천 역할을 한다. ([SoloMatchEntity]는 헤더만 보관)
 * 1:1이면 한 매칭에 두 행(남1·여1)이, N:N(2:2·3:3)이면 여러 행이 생긴다.
 * (match_id, user_id) 유니크 제약으로 한 매칭에 같은 사용자가 중복 참가하는 것을 막고, (user_id, status) 인덱스로 사용자별 참가 매칭 조회(+활성 필터)를 커버한다.
 * 참가자 상태([status])는 WAITING→APPLY→ACTIVE/DEACTIVE로 전이하며, 전원 ACTIVE면 매칭이 성사된 것이다.
 * 도메인 로직은 [com.org.oneulsogae.core.solomatch.command.domain.MatchMember] 모델에 정의한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "solo_match_members",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_match_id_user_id", columnNames = ["match_id", "user_id"]),
	],
	indexes = [
		// 사용자별 참가 매칭 조회. status를 부록으로 둬 "내 활성(ACTIVE) 매칭" 필터가 인덱스에서 DEACTIVE 행을 건너뛰게 한다.
		// (선두가 user_id라 user_id 단독 조회도 그대로 커버)
		Index(name = "idx_user_id_status", columnList = "user_id, status"),
	],
)
class SoloMatchMemberEntity(
	@Column(name = "match_id", nullable = false)
	val matchId: Long,

	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** 참가자 성별. 성별 균형 구성·성별 기반 조회에 쓴다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "gender", nullable = false, columnDefinition = "varchar(50)")
	val gender: Gender,

	/** 참가자 상태. WAITING(대기) → APPLY(신청) → ACTIVE(성사·활성) / DEACTIVE(채팅 나감). */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	var status: MatchMemberStatus = MatchMemberStatus.WAITING,

	/** 이 참가자가 매칭(소개)을 확인한 시각. 목록 조회로 처음 확인될 때 채워지며, 미확인이면 null. */
	@Column(name = "checked_at")
	var checkedAt: LocalDateTime? = null,

	/** 신청(APPLY) 시 실제 지불한 신청 비용의 스냅샷. 미신청이거나 구행 데이터면 null. (환불 산정에 쓴다) */
	@Column(name = "paid_init_amount")
	var paidInitAmount: Int? = null,
) : BaseEntity()
