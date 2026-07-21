package com.org.oneulsogae.core.lounge.command.application

import com.org.oneulsogae.common.chat.ChatRoomMatchType
import com.org.oneulsogae.common.coin.CoinUsageType
import com.org.oneulsogae.core.chat.command.application.port.`in`.SaveChatRoomUseCase
import com.org.oneulsogae.core.chat.command.application.port.`in`.command.SaveChatRoomCommand
import com.org.oneulsogae.core.chat.command.application.port.`in`.command.SaveChatRoomParticipant
import com.org.oneulsogae.core.chat.command.domain.ChatRoom
import com.org.oneulsogae.core.coin.command.application.port.`in`.SpendCoinUseCase
import com.org.oneulsogae.core.coin.command.application.port.`in`.command.SpendCoinCommand
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.event.DomainEventPublisher
import com.org.oneulsogae.core.common.lock.DistributedLock
import com.org.oneulsogae.core.common.lock.LockKeyConstraints
import com.org.oneulsogae.core.lounge.LoungeErrorCode
import com.org.oneulsogae.core.lounge.command.application.port.`in`.AcceptLoungeChatUseCase
import com.org.oneulsogae.core.lounge.command.application.port.`in`.result.AcceptLoungeChatResult
import com.org.oneulsogae.core.lounge.command.application.port.out.GetLoungeChatRequestPort
import com.org.oneulsogae.core.lounge.command.application.port.out.GetLoungePostPort
import com.org.oneulsogae.core.lounge.command.application.port.out.SaveLoungeChatRequestPort
import com.org.oneulsogae.core.lounge.command.domain.LoungeChatRequest
import com.org.oneulsogae.core.lounge.command.domain.LoungePost
import com.org.oneulsogae.core.lounge.command.domain.event.LoungeChatRequestAccepted
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [AcceptLoungeChatUseCase] 구현.
 * 신청을 로드해 소유권·중복 수락을 도메인([LoungeChatRequest.acceptBy])이 판정하게 하고,
 * 상태 전이 저장 → 수락 비용 차감 → 채팅방 생성을 같은 트랜잭션에서 처리한다. (한 단계라도 실패하면 함께 롤백)
 * 채팅방은 chat 도메인 in-port([SaveChatRoomUseCase])에 위임하며, 신청 한 건당 한 방이 되도록
 * `(match_type=LOUNGE, match_id=신청 id)`로 만든다. (chat 쪽이 이 조합으로 멱등 생성한다)
 * 작성자는 같은 글의 신청을 여러 건 수락할 수 있고, 수락할 때마다 코인을 내고 방이 하나씩 생긴다.
 *
 * 신청별 분산 락([DistributedLock])으로 보호한다. 경합 대상이 신청 한 건의 상태 전이이므로 requestId로 잠근다.
 * waitTime=0이라 겹친 요청은 즉시 실패(409)한다. (더블클릭 이중 과금 fail-fast)
 * 알람만 커밋 후 best-effort([LoungeEventHandler])다.
 */
@Service
class AcceptLoungeChatService(
	private val getLoungeChatRequestPort: GetLoungeChatRequestPort,
	private val saveLoungeChatRequestPort: SaveLoungeChatRequestPort,
	private val getLoungePostPort: GetLoungePostPort,
	private val spendCoinUseCase: SpendCoinUseCase,
	private val saveChatRoomUseCase: SaveChatRoomUseCase,
	private val domainEventPublisher: DomainEventPublisher,
) : AcceptLoungeChatUseCase {

	@DistributedLock(prefix = LockKeyConstraints.LOUNGE_CHAT_ACCEPT, keys = ["#requestId"], waitTime = 0)
	@Transactional
	override fun accept(userId: Long, requestId: Long): AcceptLoungeChatResult {
		val request: LoungeChatRequest = getLoungeChatRequestPort.findById(requestId)
			?: throw BusinessException(LoungeErrorCode.LOUNGE_CHAT_REQUEST_NOT_FOUND)
		val post: LoungePost = getLoungePostPort.findById(request.postId)
			?: throw BusinessException(LoungeErrorCode.SELF_INTRO_POST_NOT_FOUND, "셀소를 찾을 수 없습니다: ${request.postId}")

		// 소유권(내 글인가)·중복 수락 판정은 도메인이 한다.
		saveLoungeChatRequestPort.save(request.acceptBy(postAuthorUserId = post.userId, actorUserId = userId))
		spendCoinUseCase.spend(userId, SpendCoinCommand(amount = USAGE_TYPE.coinAmount, coinUsageType = USAGE_TYPE))

		// 채팅방 생성은 수락의 필수 산출물이라 같은 트랜잭션에서 동기로 처리한다. (실패 시 함께 롤백)
		val chatRoom: ChatRoom = saveChatRoomUseCase.save(
			SaveChatRoomCommand(
				matchType = ChatRoomMatchType.LOUNGE,
				matchId = request.id,
				participants = listOf(
					SaveChatRoomParticipant(userId = post.userId, teamId = null),
					SaveChatRoomParticipant(userId = request.requesterUserId, teamId = null),
				),
			),
		)

		// 알람은 부가 효과라 커밋 후 별도 트랜잭션에서 best-effort로 처리한다. ([LoungeEventHandler])
		domainEventPublisher.publish(
			LoungeChatRequestAccepted(
				requestId = request.id,
				requesterUserId = request.requesterUserId,
				postAuthorUserId = post.userId,
				chatRoomId = chatRoom.id,
			),
		)
		return AcceptLoungeChatResult(chatRoom.id)
	}

	companion object {
		/** 대화 수락 차감 유형. 금액은 이 유형의 정책값(coinAmount)을 그대로 쓴다. */
		private val USAGE_TYPE: CoinUsageType = CoinUsageType.LOUNGE_CHAT_ACCEPT
	}
}
