package com.org.oneulsogae.core.lounge.command.application

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.core.alarm.command.application.port.`in`.SaveAlarmUseCase
import com.org.oneulsogae.core.alarm.command.application.port.`in`.command.SaveAlarmCommand
import com.org.oneulsogae.core.lounge.command.domain.event.LoungeChatRequestAccepted
import com.org.oneulsogae.core.lounge.command.domain.event.LoungeChatRequested
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserDetailUseCase
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 라운지 대화 신청·수락 도메인 이벤트의 후속 알람 처리를 한곳에서 다루는 핸들러.
 *
 * 알람은 부가 효과이므로 모두 커밋 이후(AFTER_COMMIT) 별도 트랜잭션([Propagation.REQUIRES_NEW])으로 best-effort 저장한다.
 * (알람 저장이 실패해도 신청·과금·채팅방은 롤백되지 않는다)
 * 알람 저장은 alarm 도메인 in-port([SaveAlarmUseCase])로 위임하고, 문구에 쓸 닉네임은 user 도메인 in-port([GetUserDetailUseCase])로 조회한다.
 */
@Component
class LoungeEventHandler(
	private val saveAlarmUseCase: SaveAlarmUseCase,
	private val getUserDetailUseCase: GetUserDetailUseCase,
) {

	/** 대화 신청 → 글 작성자에게 "대화 신청 받음" 알람. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun onLoungeChatRequested(event: LoungeChatRequested) {
		val requesterNickname: String? = getUserDetailUseCase.findByUserId(event.requesterUserId)?.nickname

		saveAlarmUseCase.save(
			SaveAlarmCommand(
				userId = event.postAuthorUserId,
				type = AlarmType.LOUNGE_CHAT_REQUEST_RECEIVED,
				title = "새로운 대화 신청",
				description = requesterNickname
					?.let { "${it}님이 회원님에게 대화를 신청했어요." }
					?: "회원님에게 대화를 신청한 상대가 있어요.",
				// 알람을 누르면 라운지로 이동한다. (프론트 라우팅에 맞춘 경로)
				link = "/",
				fromUserId = event.requesterUserId,
			),
		)
	}

	/** 대화 신청 수락 → 신청자에게 "대화 신청 수락됨" 알람. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun onLoungeChatRequestAccepted(event: LoungeChatRequestAccepted) {
		val authorNickname: String? = getUserDetailUseCase.findByUserId(event.postAuthorUserId)?.nickname

		saveAlarmUseCase.save(
			SaveAlarmCommand(
				userId = event.requesterUserId,
				type = AlarmType.LOUNGE_CHAT_ACCEPTED,
				title = "대화 신청 수락",
				description = authorNickname
					?.let { "${it}님이 대화 신청을 수락했어요." }
					?: "상대방이 대화 신청을 수락했어요.",
				// 알람을 누르면 생성된 채팅방으로 이동한다. (프론트 라우팅에 맞춘 경로)
				link = "/chat/${event.chatRoomId}",
				fromUserId = event.postAuthorUserId,
			),
		)
	}
}
