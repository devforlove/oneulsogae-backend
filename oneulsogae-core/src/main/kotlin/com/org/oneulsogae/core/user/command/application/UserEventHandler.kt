package com.org.oneulsogae.core.user.command.application

import com.org.oneulsogae.core.common.event.MatchProfileSnapshot
import com.org.oneulsogae.core.common.event.UserLoggedIn
import com.org.oneulsogae.core.gathering.command.application.port.`in`.SyncGatheringProfileUseCase
import com.org.oneulsogae.core.matchuser.command.application.port.`in`.SyncMatchUserUseCase
import com.org.oneulsogae.core.user.command.application.port.out.GetUserDetailPort
import com.org.oneulsogae.core.user.command.application.port.out.GetUserPort
import com.org.oneulsogae.core.user.command.domain.User
import com.org.oneulsogae.core.user.command.domain.UserDetail
import com.org.oneulsogae.core.user.command.domain.event.UserProfileChanged
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * user 도메인 이벤트의 후속 처리를 한곳에서 다루는 핸들러.
 *
 * 프로필/가입 상태 변경([UserProfileChanged])·로그인([UserLoggedIn])을 받아, 자기 도메인 데이터로 매칭 가능 스냅샷을 만들고
 * match 도메인 in-port([SyncMatchUserUseCase])에 위임해 매칭 읽기 모델(match_user)을 동기화한다.
 * 프로필 변경은 gathering 도메인 in-port([SyncGatheringProfileUseCase])에도 위임해 gathering_profile의 유저 유래 필드(프로필이미지·생일·키)를 최신화한다.
 * (읽기 모델의 적재/삭제 자체는 그 모델을 소유한 도메인이 책임진다 — 다른 도메인 동작은 in-port로만 호출)
 * 정합성을 위해 커밋 직전(BEFORE_COMMIT)에 발행 트랜잭션과 같은 트랜잭션으로 동기화한다.
 * (동기화가 실패하면 프로필/가입/로그인 변경도 함께 롤백된다 — 강한 일관성)
 */
@Component
class UserEventHandler(
	private val getUserPort: GetUserPort,
	private val getUserDetailPort: GetUserDetailPort,
	private val syncMatchUserUseCase: SyncMatchUserUseCase,
	private val syncGatheringProfileUseCase: SyncGatheringProfileUseCase,
) {

	/** 프로필/가입 상태 변경 → 매칭 읽기 모델 동기화 + gathering_profile 유저 유래 필드 최신화에 위임한다. */
	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	fun onUserProfileChanged(event: UserProfileChanged) {
		val detail: UserDetail? = getUserDetailPort.findByUserId(event.userId)
		syncMatchUserUseCase.sync(event.userId, matchProfileSnapshotOf(event.userId, detail))
		detail?.let { syncGatheringProfileUseCase.sync(event.userId, it.profileImageCode, it.birthday, it.height) }
	}

	/** 로그인 → 매칭 읽기 모델의 마지막 로그인 시각 갱신에 위임한다. */
	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	fun onUserLoggedIn(event: UserLoggedIn) {
		syncMatchUserUseCase.updateLastLogin(event.userId, event.lastLoginAt)
	}

	/** 해당 사용자의 현재 상태로 매칭 가능 스냅샷을 만든다. (사용자가 없거나 프로필이 없거나 매칭 불가면 null) */
	private fun matchProfileSnapshotOf(userId: Long, detail: UserDetail?): MatchProfileSnapshot? {
		if (detail == null) return null
		val user: User = getUserPort.findById(userId) ?: return null
		return detail.matchProfileSnapshotOrNull(user.status, user.lastLoginAt)
	}
}
