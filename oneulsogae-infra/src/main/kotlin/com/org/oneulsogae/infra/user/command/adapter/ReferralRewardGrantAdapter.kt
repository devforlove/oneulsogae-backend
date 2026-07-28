package com.org.oneulsogae.infra.user.command.adapter

import com.org.oneulsogae.core.user.command.application.port.out.GetReferralRewardGrantPort
import com.org.oneulsogae.core.user.command.application.port.out.SaveReferralRewardGrantPort
import com.org.oneulsogae.core.user.command.domain.ReferralRewardGrant
import com.org.oneulsogae.infra.user.command.entity.ReferralRewardGrantEntity
import com.org.oneulsogae.infra.user.command.repository.ReferralRewardGrantJpaRepository
import org.springframework.stereotype.Component

/**
 * referral_reward_grants 영속성 어댑터. 추천 보상 지급 이력의 조회·저장 out-port를 함께 구현한다.
 * 존재 여부는 유니크 인덱스(ux_referred_di_hash) 동등 조건이라 seek 한 번으로 끝난다.
 */
@Component
class ReferralRewardGrantAdapter(
	private val referralRewardGrantJpaRepository: ReferralRewardGrantJpaRepository,
) : GetReferralRewardGrantPort, SaveReferralRewardGrantPort {

	override fun existsByReferredDiHash(referredDiHash: String): Boolean =
		referralRewardGrantJpaRepository.existsByReferredDiHash(referredDiHash)

	override fun save(grant: ReferralRewardGrant): ReferralRewardGrant {
		val saved: ReferralRewardGrantEntity = referralRewardGrantJpaRepository.save(grant.toEntity())
		return grant.copy(id = saved.id ?: 0)
	}
}

/** 도메인 모델 -> 영속성 엔티티. (지급 이력은 갱신되지 않아 신규 저장만 있다) */
private fun ReferralRewardGrant.toEntity(): ReferralRewardGrantEntity =
	ReferralRewardGrantEntity(
		referredDiHash = referredDiHash,
		referrerUserId = referrerUserId,
		referredUserId = referredUserId,
		coinAmount = coinAmount,
		grantedAt = grantedAt,
	)
