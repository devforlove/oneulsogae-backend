package com.org.meeple.core.match.command.application

import com.org.meeple.common.alarm.AlarmType
import com.org.meeple.core.alarm.command.application.port.`in`.SaveAlarmUseCase
import com.org.meeple.core.alarm.command.application.port.`in`.command.SaveAlarmCommand
import com.org.meeple.core.match.command.domain.event.TeamDisbanded
import com.org.meeple.core.match.command.domain.event.TeamInvitationAccepted
import com.org.meeple.core.match.command.domain.event.TeamInvitationCanceled
import com.org.meeple.core.match.command.domain.event.TeamInvitationDeclined
import com.org.meeple.core.match.command.domain.event.TeamInvitationSent
import com.org.meeple.core.user.query.service.port.`in`.GetUserDetailUseCase
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 팀(초대) 도메인 이벤트의 후속 알람 처리를 한곳에서 다루는 핸들러.
 *
 * 알람은 부가 효과이므로 모두 커밋 이후(AFTER_COMMIT) 별도 트랜잭션([Propagation.REQUIRES_NEW])으로 best-effort 저장한다.
 * (알람 저장이 실패해도 초대/철회는 롤백되지 않는다)
 * 알람 저장은 alarm 도메인 in-port([SaveAlarmUseCase])로 위임하고, 문구에 쓸 닉네임은 user 도메인 in-port([GetUserDetailUseCase])로 조회한다.
 */
@Component
class TeamEventHandler(
	private val saveAlarmUseCase: SaveAlarmUseCase,
	private val getUserDetailUseCase: GetUserDetailUseCase,
) {

	/** 팀 초대 → 초대받은 사람에게 "팀 초대 받음" 알람. (문구엔 초대자 닉네임) */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun onTeamInvitationSent(event: TeamInvitationSent) {
		val inviterNickname: String? = getUserDetailUseCase.findByUserId(event.inviterUserId)?.nickname

		saveAlarmUseCase.save(
			SaveAlarmCommand(
				userId = event.invitedUserId,
				type = AlarmType.TEAM_INVITATION_RECEIVED,
				title = "새로운 팀 초대",
				description = inviterNickname
					?.let { "${it}님이 회원님을 팀에 초대했어요." }
					?: "회원님을 팀에 초대한 상대가 있어요.",
				// 알람을 누르면 받은 초대 목록으로 이동한다. (프론트 라우팅에 맞춘 경로)
				link = "/friend/invites",
				fromUserId = event.inviterUserId,
				fromTeamId = event.teamId,
			),
		)
	}

	/** 초대 거절 → 초대했던 사람에게 "초대 거절됨" 알람. (문구엔 거절한 사람 닉네임) */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun onTeamInvitationDeclined(event: TeamInvitationDeclined) {
		val invitedNickname: String? = getUserDetailUseCase.findByUserId(event.invitedUserId)?.nickname

		saveAlarmUseCase.save(
			SaveAlarmCommand(
				userId = event.inviterUserId,
				type = AlarmType.TEAM_INVITATION_DECLINED,
				title = "팀 초대 거절",
				description = invitedNickname
					?.let { "${it}님이 팀 초대를 거절했어요." }
					?: "보낸 팀 초대가 거절되었어요.",
				// 알람을 누르면 팀 구성 화면으로 이동한다. (프론트 라우팅에 맞춘 경로)
				link = "/friend/team",
				fromUserId = event.invitedUserId,
				fromTeamId = event.teamId,
			),
		)
	}

	/** 초대 취소 → 초대받았던 사람에게 "초대 취소됨" 알람. (문구엔 취소한 초대자 닉네임) */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun onTeamInvitationCanceled(event: TeamInvitationCanceled) {
		val inviterNickname: String? = getUserDetailUseCase.findByUserId(event.inviterUserId)?.nickname

		saveAlarmUseCase.save(
			SaveAlarmCommand(
				userId = event.invitedUserId,
				type = AlarmType.TEAM_INVITATION_CANCELED,
				title = "팀 초대 취소",
				description = inviterNickname
					?.let { "${it}님이 팀 초대를 취소했어요." }
					?: "받은 팀 초대가 취소되었어요.",
				// 알람을 누르면 받은 초대 목록으로 이동한다. (프론트 라우팅에 맞춘 경로)
				link = "/friend/invites",
				fromUserId = event.inviterUserId,
				fromTeamId = event.teamId,
			),
		)
	}

	/** 초대 수락 → 초대했던 사람에게 "초대 수락됨" 알람. (문구엔 수락한 사람 닉네임) */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun onTeamInvitationAccepted(event: TeamInvitationAccepted) {
		val invitedNickname: String? = getUserDetailUseCase.findByUserId(event.invitedUserId)?.nickname

		saveAlarmUseCase.save(
			SaveAlarmCommand(
				userId = event.inviterUserId,
				type = AlarmType.TEAM_INVITATION_ACCEPTED,
				title = "팀 초대 수락",
				description = invitedNickname
					?.let { "${it}님이 팀 초대를 수락했어요." }
					?: "보낸 팀 초대가 수락되었어요.",
				// 알람을 누르면 팀 구성 화면으로 이동한다. (프론트 라우팅에 맞춘 경로)
				link = "/friend/team",
				fromUserId = event.invitedUserId,
				fromTeamId = event.teamId,
			),
		)
	}

	/** 팀 해체 → 해체 실행자를 제외한 같은 팀의 남은 구성원 각자에게 "팀 해체됨" 알람. (수신자 목록만큼 개별 저장) */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun onTeamDisbanded(event: TeamDisbanded) {
		event.recipientUserIds.forEach { recipientUserId: Long ->
			saveAlarmUseCase.save(
				SaveAlarmCommand(
					userId = recipientUserId,
					type = AlarmType.TEAM_DISBANDED,
					title = "팀 해체",
					description = "함께하던 팀이 해체되었어요.",
					link = "",
					fromTeamId = event.disbandedTeamId,
				),
			)
		}
	}
}
