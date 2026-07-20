package com.org.oneulsogae.core.matchuser.command.application.port.out

/**
 * 매칭 읽기 모델(match_user) 삭제 아웃포트.
 * 사용자가 매칭 불가 상태(비활성·프로필 미완성)로 전이하면 후보 풀에서 제외하기 위해 행을 제거한다.
 */
interface DeleteMatchUserPort {

	/** 해당 사용자의 매칭 읽기 모델 행을 삭제한다. 행이 없으면 아무 일도 하지 않는다. */
	fun deleteByUserId(userId: Long)
}
