package com.org.oneulsogae.core.lounge.command.application

import com.org.oneulsogae.core.lounge.command.application.port.`in`.IncreaseLoungePostViewUseCase
import com.org.oneulsogae.core.lounge.command.application.port.out.IncreaseLoungePostViewCountPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [IncreaseLoungePostViewUseCase] 구현.
 * 상세 조회마다 무조건 +1이다(중복 조회 허용, 비로그인 포함). 없는 글이면 UPDATE가 0행에 적용돼 no-op이다.
 */
@Service
class IncreaseLoungePostViewService(
	private val increaseLoungePostViewCountPort: IncreaseLoungePostViewCountPort,
) : IncreaseLoungePostViewUseCase {

	@Transactional
	override fun increase(postId: Long) {
		increaseLoungePostViewCountPort.increaseViewCount(postId)
	}
}
