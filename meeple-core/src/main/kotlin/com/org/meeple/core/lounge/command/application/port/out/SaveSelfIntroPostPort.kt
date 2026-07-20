package com.org.meeple.core.lounge.command.application.port.out

import com.org.meeple.core.lounge.command.domain.SelfIntroPost

/** 셀프 소개팅 본문 저장 out-port. */
interface SaveSelfIntroPostPort {

	fun save(post: SelfIntroPost): SelfIntroPost
}
