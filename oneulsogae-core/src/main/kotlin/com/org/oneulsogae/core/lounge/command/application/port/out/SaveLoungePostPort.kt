package com.org.oneulsogae.core.lounge.command.application.port.out

import com.org.oneulsogae.core.lounge.command.domain.LoungePost

/** 라운지 글(공통 골격) 저장 out-port. */
interface SaveLoungePostPort {

	fun save(post: LoungePost): LoungePost
}
