package com.org.meeple.core.match.command.application

import com.org.meeple.common.chat.ChatRoomMatchType
import com.org.meeple.core.chat.command.application.port.`in`.DeactivateChatRoomMemberUseCase
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.lock.DistributedLock
import com.org.meeple.core.common.lock.LockKeyConstraints
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.match.MatchErrorCode
import com.org.meeple.core.match.command.application.port.`in`.EndMatchUseCase
import com.org.meeple.core.match.command.application.port.out.GetMatchPort
import com.org.meeple.core.match.command.application.port.out.SaveMatchPort
import com.org.meeple.core.match.command.domain.Match
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [EndMatchUseCase] 구현. 성사된 1:1 매칭을 참가자 한쪽이 나간다(종료).
 * 참가자·성사 여부를 검증한 뒤 종료자 본인의 참가만 비활성화([MatchMemberStatus.DEACTIVE])하고([Match.leave]), 상대도 이미 나간 상태였다면
 * (마지막 한 명이 나가면) 그때 매칭 헤더까지 종료(CLOSED)·소프트 삭제한다. 동시에 연결된 채팅방에서 종료자 본인의 참가만 비활성화하면서
 * 상대에게 "상대방이 채팅방을 나갔어요" 안내를 남긴다([DeactivateChatRoomMemberUseCase]).
 * 채팅방은 닫지 않아 상대는 그대로 방을 유지하며 안내 메세지를 본다. 다른 도메인(chat)은 자기 out-port가 아니라 in-port로 참조한다.
 *
 * 매칭 제거와 채팅 처리는 같은 트랜잭션이라 한 단계라도 실패하면 함께 롤백된다.
 * 같은 매칭을 잠그는 분산 락([DistributedLock])으로 보호한다. 락 키는 관심 보내기와 동일한 "MATCH_INTEREST::{matchId}"라,
 * 종료와 관심(신청/수락)이 같은 매칭 행에 동시에 쓰여 발생하는 lost update(예: 종료 직후 관심으로 매칭이 되살아남)를 막는다.
 * waitTime=0이라 같은 매칭에 동시 요청이 겹치면 한쪽은 즉시 실패(409)한다.
 */
@Service
class EndMatchService(
	private val getMatchPort: GetMatchPort,
	private val saveMatchPort: SaveMatchPort,
	private val deactivateChatRoomMemberUseCase: DeactivateChatRoomMemberUseCase,
	private val timeGenerator: TimeGenerator,
) : EndMatchUseCase {

	@DistributedLock(prefix = LockKeyConstraints.MATCH_INTEREST, keys = ["#matchId"], waitTime = 0)
	@Transactional
	override fun endMatch(userId: Long, matchId: Long) {
		val match: Match = getMatchPort.findById(matchId)
			?: throw BusinessException(MatchErrorCode.MATCH_NOT_FOUND)
		match.validateTerminable(userId)

		// 종료자 본인의 참가만 비활성화(DEACTIVE)한다. 상대도 이미 나갔으면(마지막 한 명) 매칭 헤더까지 종료·소프트 삭제된다.
		val now: LocalDateTime = timeGenerator.now()
		saveMatchPort.save(match.leave(userId, now))

		// 연결된 채팅방에서 종료자 본인만 내보내고 상대에게 나감을 알린다. (방은 상대에게 유지)
		deactivateChatRoomMemberUseCase.deactivate(ChatRoomMatchType.SOLO, matchId, listOf(userId))
	}
}
