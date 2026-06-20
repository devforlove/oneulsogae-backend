package com.org.meeple.infra.match.command.mapper

import com.org.meeple.core.match.command.domain.MatchUser
import com.org.meeple.infra.match.command.entity.MatchUserEntity

/** 영속성 엔티티 -> 도메인 모델. */
fun MatchUserEntity.toDomain(): MatchUser =
	MatchUser(
		userId = userId,
		gender = gender,
		age = age,
		regionCode = regionCode,
		maritalStatus = maritalStatus,
		nickname = nickname,
		profileImageCode = profileImageCode,
		lastLoginAt = lastLoginAt,
	)

/**
 * 도메인 모델 -> 신규 영속성 엔티티. (upsert의 INSERT 경로 — id/감사 컬럼은 영속화 시 채워진다)
 */
fun MatchUser.toEntity(): MatchUserEntity =
	MatchUserEntity(
		userId = userId,
		gender = gender,
		regionCode = regionCode,
		maritalStatus = maritalStatus,
		age = age,
		nickname = nickname,
		profileImageCode = profileImageCode,
		lastLoginAt = lastLoginAt,
	)

/**
 * 기존 엔티티의 가변 필드를 도메인 값으로 갱신한다. (upsert의 UPDATE 경로)
 * id·user_id·생성 시각은 보존하고, 매칭 기준 필드만 덮어쓴다.
 */
fun MatchUserEntity.applyFrom(matchUser: MatchUser) {
	gender = matchUser.gender
	regionCode = matchUser.regionCode
	maritalStatus = matchUser.maritalStatus
	age = matchUser.age
	nickname = matchUser.nickname
	profileImageCode = matchUser.profileImageCode
	lastLoginAt = matchUser.lastLoginAt
}
