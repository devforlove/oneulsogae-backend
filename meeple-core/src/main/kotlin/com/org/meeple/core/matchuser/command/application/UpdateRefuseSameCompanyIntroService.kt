package com.org.meeple.core.matchuser.command.application

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.matchuser.MatchUserErrorCode
import com.org.meeple.core.matchuser.command.application.port.`in`.UpdateRefuseSameCompanyIntroUseCase
import com.org.meeple.core.matchuser.command.application.port.out.SaveMatchUserPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [UpdateRefuseSameCompanyIntroUseCase] 구현. 매칭 읽기 모델(match_user)의 같은 회사 소개 거부 플래그를 갱신한다.
 * 행이 없다는 것은 매칭 불가(프로필 미완성 등) 상태이므로 [MatchUserErrorCode.PROFILE_INCOMPLETE]로 거절한다.
 */
@Service
@Transactional
class UpdateRefuseSameCompanyIntroService(
	private val saveMatchUserPort: SaveMatchUserPort,
) : UpdateRefuseSameCompanyIntroUseCase {

	override fun updateRefuseSameCompanyIntro(userId: Long, refuse: Boolean) {
		val updated: Int = saveMatchUserPort.updateRefuseSameCompanyIntro(userId, refuse)
		if (updated == 0) {
			throw BusinessException(MatchUserErrorCode.PROFILE_INCOMPLETE, "매칭 프로필이 없어 같은 회사 소개 거부 설정을 변경할 수 없습니다: $userId")
		}
	}
}
