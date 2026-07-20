package com.org.oneulsogae.core.user.command.application.port.out

/** KCP 거래등록 입력. Ret_URL·site_cd 등 KCP 고정 파라미터는 어댑터가 설정에서 채운다. */
data class CertRegisterCommand(
	val ordrIdxx: String,
)
