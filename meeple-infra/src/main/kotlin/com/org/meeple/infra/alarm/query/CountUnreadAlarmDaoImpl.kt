package com.org.meeple.infra.alarm.query

import com.org.meeple.core.alarm.query.dao.CountUnreadAlarmDao
import com.org.meeple.infra.alarm.command.entity.QAlarmEntity
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 미수신 알람 개수 조회 dao([CountUnreadAlarmDao])의 QueryDSL 구현. (조회 전용)
 * 기존 (user_id, created_at) 복합 인덱스로 user_id 등치 + created_at 범위(최근 1개월) seek를 충족한다.
 * 미읽음(is_read) 필터는 인덱스 밖이지만, 좁혀진 최근 1개월 결과셋에만 적용되므로 비용이 작다.
 * (미읽음 알람이 사용자당 크게 누적되는 패턴이 보이면 (user_id, is_read, created_at) 전용 인덱스를 검토한다)
 */
@Component
class CountUnreadAlarmDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : CountUnreadAlarmDao {

	override fun countUnreadByUserIdSince(userId: Long, since: LocalDateTime): Long {
		val alarm: QAlarmEntity = QAlarmEntity.alarmEntity
		return queryFactory
			.select(alarm.count())
			.from(alarm)
			.where(
				alarm.userId.eq(userId),
				alarm.isRead.isFalse,
				alarm.createdAt.goe(since),
			)
			.fetchOne() ?: 0L
	}
}
