package com.org.oneulsogae.infra.lounge.query

import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.infra.lounge.command.entity.QLoungeChatRequestEntity
import com.org.oneulsogae.scheduler.lounge.command.application.port.out.GetExpiredLoungeChatRequestPort
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * [GetExpiredLoungeChatRequestPort] 구현. 만료된(미수락) 대화 신청 id를 조회한다.
 * 만료 = [now] 기준 expired_at 경과 + PENDING 상태. (수락된 ACCEPTED는 만료되지 않는다)
 * 엔티티 @SQLRestriction("deleted_at is null")이 자동 적용돼 이미 정리된 신청은 조회되지 않는다.
 * status 등치 + expired_at 범위를 (status, expired_at) 복합 인덱스(idx_status_expired_at)가 받친다.
 */
@Component
class GetExpiredLoungeChatRequestDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetExpiredLoungeChatRequestPort {

	override fun findExpiredRequestIds(now: LocalDateTime): List<Long> {
		val request: QLoungeChatRequestEntity = QLoungeChatRequestEntity.loungeChatRequestEntity
		return queryFactory
			.select(request.id)
			.from(request)
			.where(
				request.status.eq(LoungeChatRequestStatus.PENDING),
				request.expiredAt.lt(now),
			)
			.fetch()
	}
}
