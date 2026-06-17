package com.org.meeple.core.match.command.application

import com.org.meeple.common.alarm.AlarmType
import com.org.meeple.core.alarm.command.application.port.`in`.SaveAlarmUseCase
import com.org.meeple.core.alarm.command.application.port.`in`.command.SaveAlarmCommand
import com.org.meeple.core.user.query.service.port.`in`.GetUserDetailUseCase
import com.org.meeple.core.match.command.domain.event.InterestSent
import com.org.meeple.core.match.command.domain.event.MatchAccepted
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 매칭 도메인 이벤트의 후속 알람 처리를 한곳에서 다루는 핸들러.
 *
 * 알람은 부가 효과이므로 모두 커밋 이후(AFTER_COMMIT) 별도 트랜잭션([Propagation.REQUIRES_NEW])으로 best-effort 저장한다.
 * (알람 저장이 실패해도 관심/성사/과금/채팅방은 롤백되지 않는다)
 * 알람 저장은 alarm 도메인 in-port([SaveAlarmUseCase])로 위임하고, 문구에 쓸 닉네임은 user 도메인 in-port([GetUserDetailUseCase])로 조회한다.
 */
@Component
class MatchEventHandler(
	private val saveAlarmUseCase: SaveAlarmUseCase,
	private val getUserDetailUseCase: GetUserDetailUseCase,
) {

	/** 관심 보내기 → 관심을 받은 상대에게 "관심 받음" 알람. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun onInterestSent(event: InterestSent) {
		val senderNickname: String? = getUserDetailUseCase.findByUserId(event.senderUserId)?.nickname

		saveAlarmUseCase.save(
			SaveAlarmCommand(
				userId = event.recipientUserId,
				type = AlarmType.ONE_TO_ONE_INTEREST_RECEIVED,
				title = "새로운 관심",
				description = senderNickname
					?.let { "${it}님이 회원님에게 관심을 보냈어요." }
					?: "회원님에게 관심을 보낸 상대가 있어요.",
				// 알람을 누르면 해당 매칭으로 이동한다. (프론트 라우팅에 맞춘 경로)
				link = "/",
				fromUserId = event.senderUserId,
			),
		)
	}

	/** 매칭 성사 → 성사된 두 사람(수락자 본인 + 그 상대) 모두에게, 각자 매칭된 상대를 가리키는 "매칭 성사" 알람. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun onMatchAccepted(event: MatchAccepted) {
		// 수락자의 상대(원래 관심을 보낸 쪽)에게는 수락자를, 수락자 본인에게는 그 상대를 가리키는 알람을 보낸다.
		saveAlarmUseCase.save(matchedAlarm(recipientUserId = event.partnerOfAcceptor, matchedUserId = event.acceptedByUserId))
		saveAlarmUseCase.save(matchedAlarm(recipientUserId = event.acceptedByUserId, matchedUserId = event.partnerOfAcceptor))
	}

	// 매칭된 상대([matchedUserId])를 가리키는 "매칭 성사" 알람을 [recipientUserId]에게 보낼 커맨드를 만든다. (문구엔 상대 닉네임)
	private fun matchedAlarm(recipientUserId: Long, matchedUserId: Long): SaveAlarmCommand {
		val matchedNickname: String? = getUserDetailUseCase.findByUserId(matchedUserId)?.nickname
		return SaveAlarmCommand(
			userId = recipientUserId,
			type = AlarmType.ONE_TO_ONE_MATCHED,
			title = "매칭 성사",
			description = matchedNickname
				?.let { "${it}님과 매칭되었어요!" }
				?: "새로운 매칭이 성사되었어요!",
			// 알람을 누르면 해당 매칭으로 이동한다. (프론트 라우팅에 맞춘 경로)
			link = "/chat",
			fromUserId = matchedUserId,
		)
	}
}
