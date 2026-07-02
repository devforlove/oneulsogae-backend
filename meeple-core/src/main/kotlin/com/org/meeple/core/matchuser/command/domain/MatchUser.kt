package com.org.meeple.core.matchuser.command.domain

import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.core.common.event.MatchProfileSnapshot
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 매칭에 필요한 기준 필드만 담은 match 도메인 소유 읽기 모델.
 * user 도메인의 [MatchProfileSnapshot] 이벤트로부터 동기화되며(match_user 테이블), 후보 선정·매칭 가능 판정에 쓰인다.
 * 이 모델로 존재한다는 것 자체가 매칭 가능(정식 가입 + 필수 필드 완성)을 의미하므로 모든 필드가 non-null이다.
 */
data class MatchUser(
	val userId: Long,
	val gender: Gender,
	val birthday: LocalDate,
	val regionId: Long,
	val maritalStatus: MaritalStatus,
	val nickname: String,
	val profileImageCode: String,
	val lastLoginAt: LocalDateTime,
	/** 회사명. 같은 회사 소개 차단 판정에 쓴다. 회사 미인증이면 null. */
	val companyName: String?,
	/** 같은 회사 구성원 소개 거부 여부. 스냅샷 동기화 대상이 아닌 사용자 설정 값(신규 적재 기본 거부). */
	val refuseSameCompanyIntro: Boolean,
) {

	/** 매칭 상대로 찾을 후보의 성별. (반대 성별) */
	fun partnerGender(): Gender =
		gender.opposite()

	companion object {

		/** user 도메인이 보낸 매칭 가능 스냅샷으로부터 매칭 읽기 모델을 만든다. (거부 플래그는 신규 적재 기본값 — 기존 행 갱신 시 매퍼가 보존) */
		fun from(userId: Long, snapshot: MatchProfileSnapshot): MatchUser =
			MatchUser(
				userId = userId,
				gender = snapshot.gender,
				birthday = snapshot.birthday,
				regionId = snapshot.regionId,
				maritalStatus = snapshot.maritalStatus,
				nickname = snapshot.nickname,
				profileImageCode = snapshot.profileImageCode,
				lastLoginAt = snapshot.lastLoginAt,
				companyName = snapshot.companyName,
				refuseSameCompanyIntro = true,
			)
	}
}
