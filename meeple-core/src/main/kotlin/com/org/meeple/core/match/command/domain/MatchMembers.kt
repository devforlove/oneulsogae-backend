package com.org.meeple.core.match.command.domain

import com.org.meeple.common.user.Gender
import java.time.LocalDateTime

/**
 * 한 매칭의 참가자([MatchMember]) 목록의 일급 컬렉션(first-class collection).
 * 참가자 식별·상대 식별·수락 집계와, 재소개 방지에 쓰는 멤버 키 산출을 한곳에 응집한다.
 * 1:1이면 두 명(남1·여1)을 담고, N:N으로 확장하면 여러 명을 담는다.
 */
data class MatchMembers(
	val values: List<MatchMember>,
) {

	/** 참가자 수. */
	val size: Int
		get() = values.size

	/** 비어 있는지 여부. */
	fun isEmpty(): Boolean = values.isEmpty()

	/** [userId] 참가자를 찾는다. 없으면 null. */
	fun find(userId: Long): MatchMember? =
		values.firstOrNull { it.userId == userId }

	/** [userId]가 이 매칭의 참가자인지 여부. */
	fun isParticipant(userId: Long): Boolean =
		values.any { it.userId == userId }

	/** [userId]를 제외한 나머지(상대) 참가자들. (1:1이면 한 명) */
	fun partnersOf(userId: Long): List<MatchMember> =
		values.filter { it.userId != userId }

	/** 참가자 userId 목록. */
	fun userIds(): List<Long> =
		values.map { it.userId }

	/** 참가자 조합을 식별하는 정규화 키. (재소개 방지 유니크 키) */
	fun memberKey(): String =
		memberKeyOf(userIds())

	/** 모든 참가자가 신청했는지 여부. (참가자가 있고 전원 APPLY/ACTIVE) */
	fun allApplied(): Boolean =
		values.isNotEmpty() && values.all { it.hasApplied }

	/** 한 명이라도 신청했는지 여부. */
	fun anyApplied(): Boolean =
		values.any { it.hasApplied }

	/** 신청(코인 지불)한 참가자들. (환불 대상 산정에 쓴다) */
	fun applied(): List<MatchMember> =
		values.filter { it.hasApplied }

	/** [userId] 참가자를 신청(APPLY) 처리한 새 컬렉션을 반환한다. */
	fun apply(userId: Long): MatchMembers =
		MatchMembers(values.map { if (it.userId == userId) it.apply() else it })

	/** 모든 참가자를 활성(ACTIVE)으로 승격한 새 컬렉션을 반환한다. (매치 성사 시) */
	fun activateAll(): MatchMembers =
		MatchMembers(values.map { it.activate() })

	/** [userId] 참가자만 비활성([MatchMember.deactivate]) 전이한 새 컬렉션을 반환한다. (없으면 그대로) */
	fun deactivate(userId: Long): MatchMembers =
		MatchMembers(values.map { if (it.userId == userId) it.deactivate() else it })

	/** 모든 참가자를 [now]에 소프트 삭제(제거)한 새 컬렉션을 반환한다. */
	fun delete(now: LocalDateTime): MatchMembers =
		MatchMembers(values.map { it.delete(now) })

	companion object {

		/** (userId, gender) 쌍들로 참가자 목록을 만든다. (matchId는 저장 시 채워진다) */
		fun of(participants: List<Pair<Long, Gender>>): MatchMembers =
			MatchMembers(
				participants.map { (userId: Long, gender: Gender) ->
					MatchMember(matchId = 0, userId = userId, gender = gender)
				},
			)

		/** 참가자 userId들을 정렬해 이어 붙인 정규화 키. (순서와 무관하게 같은 조합이면 같은 키) */
		fun memberKeyOf(userIds: List<Long>): String =
			userIds.sorted().joinToString("-")
	}
}
