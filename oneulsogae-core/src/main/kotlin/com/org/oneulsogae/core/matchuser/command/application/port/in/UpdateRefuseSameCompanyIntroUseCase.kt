package com.org.oneulsogae.core.matchuser.command.application.port.`in`

/**
 * 같은 회사 구성원 소개 거부 플래그 변경 유스케이스.
 * 매칭 읽기 모델(match_user)에 적재된 사용자만 변경할 수 있다. (행이 없으면 매칭 프로필 미완성 에러)
 */
interface UpdateRefuseSameCompanyIntroUseCase {

	/** 사용자의 같은 회사 소개 거부 플래그를 [refuse]로 변경한다. */
	fun updateRefuseSameCompanyIntro(userId: Long, refuse: Boolean)
}
