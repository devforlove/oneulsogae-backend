package com.org.meeple.core.match.command.domain

import com.org.meeple.common.user.Gender

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

	/** 모든 참가자가 수락했는지 여부. (참가자가 있고 전원 수락) */
	fun allAccepted(): Boolean =
		values.isNotEmpty() && values.all { it.isAccepted }

	/** 한 명이라도 수락했는지 여부. */
	fun anyAccepted(): Boolean =
		values.any { it.isAccepted }

	/** [userId] 참가자를 수락 처리한 새 컬렉션을 반환한다. */
	fun accept(userId: Long): MatchMembers =
		MatchMembers(values.map { if (it.userId == userId) it.accept() else it })

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
