package com.org.meeple.core.match.command.application.port.out

import com.org.meeple.core.match.command.domain.Match

/**
 * 매칭 저장 아웃포트.
 * 신규 소개를 저장하거나, 기존 매칭(id 존재)의 응답/상태 변경분을 반영한다.
 */
interface SaveMatchPort {

	fun save(match: Match): Match
}
