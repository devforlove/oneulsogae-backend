package com.org.oneulsogae.core.mission.command.application

import com.org.oneulsogae.common.coin.CoinGetType
import com.org.oneulsogae.core.coin.command.application.port.`in`.AcquireCoinUseCase
import com.org.oneulsogae.core.coin.command.application.port.`in`.command.AcquireCoinCommand
import com.org.oneulsogae.core.coin.command.domain.CoinBalance
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.mission.MissionErrorCode
import com.org.oneulsogae.core.mission.application.evaluator.MissionEvaluators
import com.org.oneulsogae.core.mission.command.application.port.`in`.ClaimMissionUseCase
import com.org.oneulsogae.core.mission.command.application.port.`in`.result.ClaimMissionResult
import com.org.oneulsogae.core.mission.command.application.port.out.SaveMissionCompletionPort
import com.org.oneulsogae.core.mission.query.dto.Mission
import com.org.oneulsogae.core.mission.query.service.port.`in`.GetMissionUseCase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [ClaimMissionUseCase] 구현.
 * ① 활성 미션 로드(없으면 404) ② 평가자로 자격 재검증(부적격이면 400 — 클라 표시와 무관하게 서버가 실제 상태로 판정)
 * ③ 코인 적립([AcquireCoinUseCase])과 완료 가드([SaveMissionCompletionPort])를 **한 트랜잭션**에서 처리한다.
 * (user_id, mission_id) 유니크 위반(이미 수령)이면 트랜잭션이 롤백돼 적립도 취소되고 409([MissionErrorCode.MISSION_ALREADY_COMPLETED])로 매핑한다.
 * (이 원자성이 이중 수령을 원천 차단한다 — coin 패키지의 AcquirePurchasedCoinService와 동형)
 */
@Service
class ClaimMissionService(
	private val getMissionUseCase: GetMissionUseCase,
	private val missionEvaluators: MissionEvaluators,
	private val acquireCoinUseCase: AcquireCoinUseCase,
	private val saveMissionCompletionPort: SaveMissionCompletionPort,
) : ClaimMissionUseCase {

	@Transactional
	override fun claim(userId: Long, missionId: Long): ClaimMissionResult {
		val mission: Mission = getMissionUseCase.getById(missionId)

		if (!missionEvaluators.resolve(mission.type).isEligible(userId)) {
			throw BusinessException(MissionErrorCode.MISSION_NOT_ELIGIBLE)
		}

		val balance: CoinBalance = acquireCoinUseCase.acquire(
			userId,
			AcquireCoinCommand(amount = mission.rewardCoin, coinType = CoinGetType.MISSION),
		)
		try {
			saveMissionCompletionPort.save(userId, missionId, mission.rewardCoin)
		} catch (_: DataIntegrityViolationException) {
			// 자격 검증과 적립 사이 경합으로 완료 가드가 먼저 들어간 경우. 트랜잭션 롤백 → 적립 취소.
			throw BusinessException(MissionErrorCode.MISSION_ALREADY_COMPLETED)
		}

		return ClaimMissionResult(rewardedCoin = mission.rewardCoin, balance = balance.balance)
	}
}
