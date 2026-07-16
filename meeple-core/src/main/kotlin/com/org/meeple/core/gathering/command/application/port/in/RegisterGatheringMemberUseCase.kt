package com.org.meeple.core.gathering.command.application.port.`in`

import com.org.meeple.core.gathering.command.application.port.`in`.command.RegisterGatheringMemberCommand
import com.org.meeple.core.gathering.command.application.port.`in`.result.RegisterGatheringMemberResult

/**
 * 모임 일정 참가 접수 인포트(유스케이스). 결제완료(payments)가 호출한다.
 * 판매 상태·성별 여분·중복 신청을 검증하고 승인대기(PENDING)로 등록하며, 서버 확정가를 돌려준다.
 */
interface RegisterGatheringMemberUseCase {

	fun register(command: RegisterGatheringMemberCommand): RegisterGatheringMemberResult
}
