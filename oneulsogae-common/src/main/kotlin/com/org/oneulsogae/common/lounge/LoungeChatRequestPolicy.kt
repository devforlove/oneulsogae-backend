package com.org.oneulsogae.common.lounge

import java.time.Duration

/**
 * 라운지 셀소 대화 신청 정책 상수.
 * command(수락 차단)와 query(목록 제외)가 같은 만료 기준을 공유해야 해서 common에 둔다.
 * (query는 command 도메인을 참조할 수 없다)
 */
object LoungeChatRequestPolicy {

	/** 대화 신청의 유효 기간(3일). 신청 시각으로부터 이 기간이 지난 PENDING 신청은 만료로 본다. */
	val EXPIRATION: Duration = Duration.ofDays(3)
}
