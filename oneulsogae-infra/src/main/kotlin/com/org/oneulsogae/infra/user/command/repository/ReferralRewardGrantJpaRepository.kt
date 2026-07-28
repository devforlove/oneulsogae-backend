package com.org.oneulsogae.infra.user.command.repository

import com.org.oneulsogae.infra.user.command.entity.ReferralRewardGrantEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ReferralRewardGrantJpaRepository : JpaRepository<ReferralRewardGrantEntity, Long> {

	fun existsByReferredDiHash(referredDiHash: String): Boolean
}
