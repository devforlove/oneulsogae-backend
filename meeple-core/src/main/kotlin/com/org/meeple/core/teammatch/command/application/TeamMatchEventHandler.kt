package com.org.meeple.core.teammatch.command.application

import com.org.meeple.common.alarm.AlarmType
import com.org.meeple.core.alarm.command.application.port.`in`.SaveAlarmUseCase
import com.org.meeple.core.alarm.command.application.port.`in`.command.SaveAlarmCommand
import com.org.meeple.core.teammatch.command.domain.event.TeamMatchAccepted
import com.org.meeple.core.teammatch.command.domain.event.TeamMatchEnded
import com.org.meeple.core.teammatch.command.domain.event.TeamMatchInterestSent
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 팀 매칭(관심/성사) 도메인 이벤트의 후속 알림 처리를 한곳에서 다루는 핸들러.
 * (팀 초대 관련은 [TeamEventHandler], 1:1 매칭은 [MatchEventHandler]가 담당한다)
 *
 * 알림은 부가 효과이므로 커밋 이후(AFTER_COMMIT) 별도 트랜잭션([Propagation.REQUIRES_NEW])으로 best-effort 저장한다.
 * (알림 저장이 실패해도 관심/성사/과금/채팅방은 롤백되지 않는다)
 * 팀 단위 알림이라 개인 닉네임 대신 팀 수준 문구를 쓴다. (한 팀은 2인이라 한 명의 닉네임 노출이 부적절)
 */
@Component
class TeamMatchEventHandler(
	private val saveAlarmUseCase: SaveAlarmUseCase,
) {

	/** 팀 관심 보내기 → 상대 팀 구성원들에게 "관심 받음" 알림. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun onTeamMatchInterestSent(event: TeamMatchInterestSent) {
		event.recipientUserIds.forEach { recipientUserId: Long ->
			saveAlarmUseCase.save(
				SaveAlarmCommand(
					userId = recipientUserId,
					type = AlarmType.MANY_TO_MANY_INTEREST_RECEIVED,
					title = "새로운 관심",
					description = "상대 팀이 회원님 팀에 관심을 보냈어요.",
					link = "/meeting",
					fromTeamId = event.senderTeamId,
				),
			)
		}
	}

	/** 팀 매칭 성사 → 행위자를 제외한 양 팀 구성원들에게 "매칭 성사" 알림. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun onTeamMatchAccepted(event: TeamMatchAccepted) {
		event.recipientUserIds.forEach { recipientUserId: Long ->
			saveAlarmUseCase.save(
				SaveAlarmCommand(
					userId = recipientUserId,
					type = AlarmType.MANY_TO_MANY_MATCHED,
					title = "매칭 성사",
					description = "팀 매칭이 성사되었어요!",
					link = "/chat",
					fromTeamId = event.fromTeamId,
				),
			)
		}
	}

	/** 팀 매칭 종료(한 팀이 나감) → 방에 남는 상대 팀 구성원들에게 "매칭 종료" 알림. (마지막 종료면 이벤트가 발행되지 않아 호출되지 않는다) */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun onTeamMatchEnded(event: TeamMatchEnded) {
		event.recipientUserIds.forEach { recipientUserId: Long ->
			saveAlarmUseCase.save(
				SaveAlarmCommand(
					userId = recipientUserId,
					type = AlarmType.MANY_TO_MANY_MATCH_ENDED,
					title = "매칭 종료",
					description = "상대 팀이 매칭을 종료했어요.",
					link = "/meeting",
					fromTeamId = event.fromTeamId,
				),
			)
		}
	}
}
