package com.org.meeple.core.match.command.application.port.out

import com.org.meeple.core.match.command.domain.Match

/**
 * 매칭 제거 아웃포트.
 * 주어진 매칭(헤더+참가자)을 제거(소프트 삭제)만 한다. 조회·존재 판정 등 흐름은 호출 측(유스케이스) 책임이다.
 */
interface DeleteMatchPort {

	fun delete(match: Match)
}
