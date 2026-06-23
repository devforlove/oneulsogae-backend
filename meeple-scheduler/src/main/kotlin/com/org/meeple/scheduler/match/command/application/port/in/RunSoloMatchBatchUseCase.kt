package com.org.meeple.scheduler.match.command.application.port.`in`

import com.org.meeple.scheduler.match.command.domain.MatchBatchResult

/**
 * 일일 매칭 배치 인포트(유스케이스).
 * 매칭 대상 사용자(정식 가입 + 성별 입력 + 최근 로그인)를 순회하며, 오늘 소개가 없는 사용자에게 한 명을 소개한다.
 * 배치 실행 주체(meeple-scheduler)가 정해진 시각에 호출한다. 개별 사용자 처리 실패가 전체 배치를 멈추지 않는다.
 */
interface RunSoloMatchBatchUseCase {

	fun run(): MatchBatchResult
}
