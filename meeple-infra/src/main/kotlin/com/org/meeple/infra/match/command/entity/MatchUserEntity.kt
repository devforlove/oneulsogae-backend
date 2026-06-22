package com.org.meeple.infra.match.command.entity

import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 매칭 읽기 모델(match_user) 영속성 엔티티. match 도메인이 소유하며, user 도메인 이벤트로부터 동기화된다.
 * 매칭에 필요한 기준 필드만 담고, 행의 존재 자체가 "매칭 가능(정식 가입 + 필수 필드 완성)"을 의미한다.
 * (그래서 후보 조회는 status·null 검사·user_details 조인 없이 이 테이블 단독으로 수행한다)
 * 식별자/감사 컬럼은 [BaseEntity]가 제공하고, user_id에 유니크 제약을 둬 사용자당 한 행(upsert/삭제 키)을 보장한다.
 * 소프트 삭제 대신 매칭 불가 전이 시 행을 하드 삭제한다.
 */
@Entity
@Table(
	name = "match_user",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_user_id", columnNames = ["user_id"]),
	],
	indexes = [
		// 온보딩 추천 후보 선정(반대 성별·같은 권역·최근 로그인)용
		Index(name = "idx_gender_region_code_last_login_at", columnList = "gender, region_code, last_login_at"),
		// 일일 배치 대상 (lastLoginAt, userId) 키셋 스캔용
		Index(name = "idx_last_login_at_user_id", columnList = "last_login_at, user_id"),
		// 초대 가능 유저 닉네임 검색(nickname=, gender= seek + userId 정렬)용
		Index(name = "idx_nickname_gender_user_id", columnList = "nickname, gender, user_id"),
	],
)
class MatchUserEntity(
	@Column(name = "user_id", nullable = false, updatable = false)
	val userId: Long,

	@Enumerated(EnumType.STRING)
	@Column(name = "gender", nullable = false)
	var gender: Gender,

	@Column(name = "region_code", nullable = false)
	var regionCode: Int,

	@Enumerated(EnumType.STRING)
	@Column(name = "marital_status", nullable = false)
	var maritalStatus: MaritalStatus,

	@Column(name = "birthday", nullable = false)
	var birthday: LocalDate,

	@Column(name = "nickname", nullable = false)
	var nickname: String,

	@Column(name = "profile_image_code", nullable = false)
	var profileImageCode: String,

	@Column(name = "last_login_at", nullable = false)
	var lastLoginAt: LocalDateTime,
) : BaseEntity()
