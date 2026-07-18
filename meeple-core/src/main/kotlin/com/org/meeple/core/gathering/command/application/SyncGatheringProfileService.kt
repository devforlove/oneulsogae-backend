package com.org.meeple.core.gathering.command.application

import com.org.meeple.core.gathering.command.application.port.`in`.SyncGatheringProfileUseCase
import com.org.meeple.core.gathering.command.application.port.out.UpdateGatheringProfilePort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * [SyncGatheringProfileUseCase] 구현. gathering_profile의 유저 유래 필드를 최신 프로필 값으로 맞춘다.
 * (gathering_profile 행이 없으면 out-port가 no-op — 멤버 인증 미승인 유저)
 */
@Service
@Transactional
class SyncGatheringProfileService(
	private val updateGatheringProfilePort: UpdateGatheringProfilePort,
) : SyncGatheringProfileUseCase {

	override fun sync(userId: Long, profileImageCode: String?, birthday: LocalDate?, height: Int?) {
		updateGatheringProfilePort.updateUserFields(userId, profileImageCode, birthday, height)
	}
}
