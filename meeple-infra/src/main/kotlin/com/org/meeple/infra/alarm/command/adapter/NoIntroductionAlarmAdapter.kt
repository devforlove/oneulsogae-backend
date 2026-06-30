package com.org.meeple.infra.alarm.command.adapter

import com.org.meeple.common.alarm.AlarmType
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.core.alarm.command.application.port.`in`.SaveAlarmUseCase
import com.org.meeple.core.alarm.command.application.port.`in`.command.SaveAlarmCommand
import com.org.meeple.infra.alarm.command.repository.AlarmJpaRepository
import com.org.meeple.infra.teammatch.command.entity.TeamMemberEntity
import com.org.meeple.infra.teammatch.command.repository.TeamMemberJpaRepository
import com.org.meeple.scheduler.common.command.application.port.out.NoIntroductionAlarmPort
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * scheduler의 [NoIntroductionAlarmPort] 구현 어댑터. (scheduler는 core에 의존하지 않으므로 infra가 중개)
 * 일일 배치에서 끝까지 소개받지 못한 대상에게 "오늘 소개 없음" 알람을 저장한다.
 * 알람 저장은 core 알람 in-port([SaveAlarmUseCase])로 위임하고, 팀의 알림 수신자(활성 구성원)는 infra의 [TeamMemberJpaRepository]로 푼다.
 * 배치가 하루에 여러 번 돌아도 중복 알림이 가지 않도록, 당일 이미 같은 알림을 받은 수신자는 [AlarmJpaRepository]로 걸러낸다.
 * 알람은 부가 효과이므로 수신자 단위로 best-effort 격리한다. (한 명 저장 실패가 배치나 다른 수신자에 전파되지 않는다)
 */
@Component
class NoIntroductionAlarmAdapter(
	private val saveAlarmUseCase: SaveAlarmUseCase,
	private val teamMemberJpaRepository: TeamMemberJpaRepository,
	private val alarmJpaRepository: AlarmJpaRepository,
) : NoIntroductionAlarmPort {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	override fun notifySoloUnmatched(userIds: Collection<Long>, now: LocalDateTime) {
		freshRecipients(userIds, AlarmType.ONE_TO_ONE_NO_MATCH_TODAY, now).forEach { userId: Long ->
			saveQuietly(userId) {
				SaveAlarmCommand(
					userId = userId,
					type = AlarmType.ONE_TO_ONE_NO_MATCH_TODAY,
					title = "오늘의 소개",
					description = "오늘은 새로 추천할 상대를 찾지 못했어요. 내일 다시 찾아볼게요.",
					link = "/",
				)
			}
		}
	}

	override fun notifyTeamUnmatched(teamIds: Collection<Long>, now: LocalDateTime) {
		// 상대 팀이 유발한 알림이 아니므로 fromTeamId는 두지 않는다. (소개 자체가 없었던 결과 알림)
		val memberUserIds: List<Long> = teamIds.flatMap { teamId: Long -> activeMemberUserIds(teamId) }
		freshRecipients(memberUserIds, AlarmType.MANY_TO_MANY_NO_MATCH_TODAY, now).forEach { userId: Long ->
			saveQuietly(userId) {
				SaveAlarmCommand(
					userId = userId,
					type = AlarmType.MANY_TO_MANY_NO_MATCH_TODAY,
					title = "오늘의 팀 소개",
					description = "오늘은 우리 팀과 어울리는 상대 팀을 찾지 못했어요. 내일 다시 찾아볼게요.",
					link = "/",
				)
			}
		}
	}

	// [now]가 속한 당일 이미 [type] 알람을 받은 수신자를 제외한, 이번에 보낼 대상만 추린다. (중복 알림 차단)
	private fun freshRecipients(userIds: Collection<Long>, type: AlarmType, now: LocalDateTime): Set<Long> {
		val recipients: Set<Long> = userIds.toSet()
		if (recipients.isEmpty()) return emptySet()
		val alreadyAlarmed: Set<Long> = alarmJpaRepository
			.findAlarmedUserIds(recipients, type, now.toLocalDate().atStartOfDay())
			.toSet()
		return recipients - alreadyAlarmed
	}

	private fun activeMemberUserIds(teamId: Long): List<Long> =
		teamMemberJpaRepository.findByTeamId(teamId)
			.filter { member: TeamMemberEntity -> member.status == TeamMemberStatus.ACTIVE }
			.map { member: TeamMemberEntity -> member.userId }

	// 한 수신자 알람 저장을 best-effort로 실행한다. (실패해도 로깅 후 다음 수신자로 진행 — 배치를 막지 않는다)
	private fun saveQuietly(userId: Long, command: () -> SaveAlarmCommand) {
		try {
			saveAlarmUseCase.save(command())
		} catch (e: Exception) {
			log.warn("오늘 소개 없음 알람 저장 실패 userId={}", userId, e)
		}
	}
}
