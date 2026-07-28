package com.org.oneulsogae.core.user.command.application.port.out

import com.org.oneulsogae.core.user.command.domain.ReferralRewardGrant

/** 추천 보상 지급 이력 저장 out-port. */
interface SaveReferralRewardGrantPort {

	fun save(grant: ReferralRewardGrant): ReferralRewardGrant
}
