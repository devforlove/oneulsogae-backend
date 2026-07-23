package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.infra.lounge.command.entity.LoungeChatRequestEntity
import java.time.LocalDateTime

/** [LoungeChatRequestEntity] 테스트 픽스처. 기본은 아직 수락되지 않았고(PENDING) 만료되지 않은 신청이다. */
object LoungeChatRequestEntityFixture {

	fun create(
		postId: Long = 1L,
		requesterUserId: Long = 1L,
		receiverUserId: Long = 2L,
		status: LoungeChatRequestStatus = LoungeChatRequestStatus.PENDING,
		expiredAt: LocalDateTime = LocalDateTime.now().plusDays(3),
	): LoungeChatRequestEntity =
		LoungeChatRequestEntity(
			postId = postId,
			requesterUserId = requesterUserId,
			receiverUserId = receiverUserId,
			status = status,
			expiredAt = expiredAt,
		)
}
