package com.org.meeple.infra.alarm.query

import com.org.meeple.core.alarm.query.dao.GetAlarmDao
import com.org.meeple.core.alarm.query.dto.AlarmView
import com.org.meeple.core.alarm.query.dto.AlarmViews
import com.org.meeple.infra.alarm.command.entity.QAlarmEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 알람 조회 dao([GetAlarmDao])의 QueryDSL 구현. (조회 전용)
 * 사용자의 [since](포함) 이후 알람을 생성 시각 내림차순(동률이면 id 내림차순)으로 [AlarmViews] read model에 직접 투영한다.
 * (user_id, created_at) 복합 인덱스로 등치+범위+정렬을 filesort 없이 충족한다. (id 정렬은 인덱스의 PK 부록으로 커버)
 * 응답은 최신순 [MAX_ALARMS]건으로 제한한다. (한 달 윈도우 안 폭증 사용자 방어)
 * 저장 out-port([com.org.meeple.core.alarm.command.application.port.out.SaveAlarmPort])는 [com.org.meeple.infra.alarm.command.adapter.AlarmAdapter]가 따로 구현한다.
 */
@Component
class GetAlarmDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetAlarmDao {

	override fun findByUserIdSince(userId: Long, since: LocalDateTime): AlarmViews {
		val alarm: QAlarmEntity = QAlarmEntity.alarmEntity
		val views: List<AlarmView> = queryFactory
			.select(
				Projections.constructor(
					AlarmView::class.java,
					alarm.id,
					alarm.type,
					alarm.title,
					alarm.description,
					alarm.link,
					alarm.fromUserId,
					alarm.fromTeamId,
					alarm.isRead,
					alarm.createdAt,
				),
			)
			.from(alarm)
			.where(
				alarm.userId.eq(userId),
				alarm.createdAt.goe(since),
			)
			.orderBy(alarm.createdAt.desc(), alarm.id.desc())
			.limit(MAX_ALARMS)
			.fetch()
		return AlarmViews(views)
	}

	companion object {
		/**
		 * 조회 응답 상한(방어적 캡). 한 달 윈도우 안에서 알람이 폭증한 사용자에게도 응답 크기를 한정한다.
		 * 정렬이 (user_id, created_at) 인덱스로 커버되므로 인덱스 seek 후 조기 종료라 비용이 거의 없다.
		 */
		private const val MAX_ALARMS: Long = 100L
	}
}
