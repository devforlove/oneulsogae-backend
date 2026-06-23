package com.org.meeple.infra.match.command.adapter

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.command.application.port.out.MatchPoolPort
import com.org.meeple.scheduler.match.command.application.port.out.SaveMatchPoolPort
import com.org.meeple.scheduler.match.command.domain.MatchPoolGroup
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * [SaveMatchPoolPort] · [MatchPoolPort]의 Redis 구현 어댑터.
 * userId들을 Redis Set으로 저장하고, 소개 배치가 근접 지역 순서대로 거기서 후보를 꺼내(pop) 소비한다.
 * 매 배치마다 기존 키를 지우고 새로 적재하며, TTL로 자동 만료시켜 다음 배치가 돌지 않아도 오래된 풀이 남지 않게 한다.
 * 키 형식: (성별,지역) 풀 match:pool:{gender}:{regionId}
 */
@Component
class MatchRedisAdapter(
	private val stringRedisTemplate: StringRedisTemplate,
) : SaveMatchPoolPort, MatchPoolPort {

	override fun save(group: MatchPoolGroup) {
		replaceSet(keyOf(group.gender, group.regionId), group.userIds)
	}

	// 기존 키를 지우고 userId들을 Set으로 새로 적재한 뒤 TTL을 건다. (비어 있으면 키를 지운 상태로 둔다)
	private fun replaceSet(key: String, userIds: List<Long>) {
		stringRedisTemplate.delete(key)
		if (userIds.isEmpty()) return

		val members: Array<String> = userIds.map { id: Long -> id.toString() }.toTypedArray()
		stringRedisTemplate.opsForSet().add(key, *members)
		stringRedisTemplate.expire(key, POOL_TTL)
	}

	// SPOP: Set에서 무작위 한 명을 꺼내며 동시에 제거한다. 비어 있으면 null.
	override fun pop(gender: Gender, regionId: Long): Long? =
		stringRedisTemplate.opsForSet().pop(keyOf(gender, regionId))?.toLong()

	// SADD: 되돌릴 후보들을 다시 넣는다. (마지막 멤버를 pop해 키가 사라졌다가 재생성될 수 있어 TTL을 다시 건다)
	override fun pushBack(gender: Gender, regionId: Long, userIds: List<Long>) {
		if (userIds.isEmpty()) return

		val key: String = keyOf(gender, regionId)
		val members: Array<String> = userIds.map { id: Long -> id.toString() }.toTypedArray()
		stringRedisTemplate.opsForSet().add(key, *members)
		stringRedisTemplate.expire(key, POOL_TTL)
	}

	// SREM: 매칭된 사용자를 풀에서 제거한다. (없는 멤버면 no-op)
	override fun remove(gender: Gender, regionId: Long, userId: Long) {
		stringRedisTemplate.opsForSet().remove(keyOf(gender, regionId), userId.toString())
	}

	private fun keyOf(gender: Gender, regionId: Long): String =
		"$KEY_PREFIX$gender:$regionId"

	companion object {
		private const val KEY_PREFIX: String = "match:pool:"

		/** 풀 만료 기간. 배치 주기(1일)보다 길게 잡아 다음 배치 전까지 풀이 유지되게 한다. */
		private val POOL_TTL: Duration = Duration.ofDays(2)
	}
}
