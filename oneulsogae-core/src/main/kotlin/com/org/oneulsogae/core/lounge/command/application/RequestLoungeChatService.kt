package com.org.oneulsogae.core.lounge.command.application

import com.org.oneulsogae.common.coin.CoinUsageType
import com.org.oneulsogae.core.coin.command.application.port.`in`.SpendCoinUseCase
import com.org.oneulsogae.core.coin.command.application.port.`in`.command.SpendCoinCommand
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.event.DomainEventPublisher
import com.org.oneulsogae.core.common.lock.DistributedLock
import com.org.oneulsogae.core.common.lock.LockKeyConstraints
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.lounge.LoungeErrorCode
import com.org.oneulsogae.core.lounge.command.application.port.`in`.RequestLoungeChatUseCase
import com.org.oneulsogae.core.lounge.command.application.port.`in`.result.RequestLoungeChatResult
import com.org.oneulsogae.core.lounge.command.application.port.out.GetLoungeChatRequestPort
import com.org.oneulsogae.core.lounge.command.application.port.out.GetLoungePostPort
import com.org.oneulsogae.core.lounge.command.application.port.out.SaveLoungeChatRequestPort
import com.org.oneulsogae.core.lounge.command.domain.LoungeChatRequest
import com.org.oneulsogae.core.lounge.command.domain.LoungePost
import com.org.oneulsogae.core.user.query.dto.UserDetailView
import com.org.oneulsogae.core.user.query.service.port.`in`.CheckCompanyVerifiedUseCase
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserDetailUseCase
import com.org.oneulsogae.core.lounge.command.domain.event.LoungeChatRequested
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [RequestLoungeChatUseCase] 구현.
 * 글 존재·중복 신청을 확인한 뒤 신청을 저장하고 신청 비용을 차감한다.
 * 본인 글 신청 차단과 이성 여부 판정은 도메인([LoungeChatRequest.create])이 한다.
 * 성별은 user 도메인 in-port([GetUserDetailUseCase])로 조회해 파라미터로 넘긴다. (도메인은 인프라를 모른다)
 * 회사 인증 여부도 user 도메인 in-port([CheckCompanyVerifiedUseCase])로 검증한다. (미인증이면 코인 차감 전에 403으로 막는다)
 * 차감액은 신청자 성별 기준 [CoinUsageType.LOUNGE_CHAT_INIT] 정책값이라 클라이언트가 금액을 정하지 않는다.
 * 코인 도메인은 자기 out-port가 아니라 in-port([SpendCoinUseCase])로 참조한다.
 * 신청 저장과 코인 차감은 같은 트랜잭션이라 한 단계라도 실패하면 함께 롤백된다.
 * 알람만 커밋 후 best-effort([LoungeEventHandler])다.
 *
 * (postId, userId) 분산 락([DistributedLock])으로 보호한다. 경합 대상이 "이 사용자가 이 글에 신청했는가"라는
 * 유니크 조건이므로 글 단위가 아니라 글+사용자로 잠근다. (글로 잠그면 서로 다른 신청자끼리 불필요하게 직렬화된다)
 * waitTime=0이라 겹친 요청은 즉시 실패(409)한다. (더블클릭 이중 과금 fail-fast)
 */
@Service
class RequestLoungeChatService(
	private val getLoungePostPort: GetLoungePostPort,
	private val getLoungeChatRequestPort: GetLoungeChatRequestPort,
	private val saveLoungeChatRequestPort: SaveLoungeChatRequestPort,
	private val spendCoinUseCase: SpendCoinUseCase,
	private val getUserDetailUseCase: GetUserDetailUseCase,
	private val checkCompanyVerifiedUseCase: CheckCompanyVerifiedUseCase,
	private val domainEventPublisher: DomainEventPublisher,
	private val timeGenerator: TimeGenerator,
) : RequestLoungeChatUseCase {

	@DistributedLock(prefix = LockKeyConstraints.LOUNGE_CHAT_REQUEST, keys = ["#postId", "#userId"], waitTime = 0)
	@Transactional
	override fun request(userId: Long, postId: Long): RequestLoungeChatResult {
		// 회사 인증을 마친 사용자만 라운지 대화신청을 할 수 있다. 코인 차감 전에 막아 미인증 요청에 과금이 생기지 않게 한다.
		checkCompanyVerifiedUseCase.validateCompanyVerified(userId)

		val post: LoungePost = getLoungePostPort.findById(postId)
			?: throw BusinessException(LoungeErrorCode.SELF_INTRO_POST_NOT_FOUND, "셀소를 찾을 수 없습니다: $postId")

		if (getLoungeChatRequestPort.existsByPostIdAndRequesterUserId(postId, userId)) {
			throw BusinessException(LoungeErrorCode.LOUNGE_CHAT_REQUEST_DUPLICATED)
		}

		// 이성 여부 판정에 쓸 성별은 user 도메인 in-port로 얻는다. (프로필이 없으면 성별을 확인할 수 없어 도메인이 막는다)
		val requesterDetail: UserDetailView? = getUserDetailUseCase.findByUserId(userId)
		val postAuthorDetail: UserDetailView? = getUserDetailUseCase.findByUserId(post.userId)

		val initCoinAmount: Int = USAGE_TYPE.coinAmount(requesterDetail?.gender)
		val saved: LoungeChatRequest = saveLoungeChatRequestPort.save(
			LoungeChatRequest.create(
				postId = postId,
				requesterUserId = userId,
				postAuthorUserId = post.userId,
				requesterGender = requesterDetail?.gender,
				postAuthorGender = postAuthorDetail?.gender,
				now = timeGenerator.now(),
				initCoinAmount = initCoinAmount,
			),
		)
		spendCoinUseCase.spend(userId, SpendCoinCommand(amount = initCoinAmount, coinUsageType = USAGE_TYPE))

		// 알람은 부가 효과라 커밋 후 별도 트랜잭션에서 best-effort로 처리한다. ([LoungeEventHandler])
		domainEventPublisher.publish(
			LoungeChatRequested(
				requestId = saved.id,
				requesterUserId = userId,
				postAuthorUserId = post.userId,
			),
		)

		return RequestLoungeChatResult(saved.id)
	}

	companion object {
		/** 대화 신청 차감 유형. 금액은 이 유형의 신청자 성별별 정책값(coinAmount(gender))을 쓴다. */
		private val USAGE_TYPE: CoinUsageType = CoinUsageType.LOUNGE_CHAT_INIT
	}
}
