package com.org.oneulsogae.core.user.command.application.port.out

/** KCP 본인확인 거래등록 아웃포트. (testcert/cert.kcp.co.kr/api/reg/certDataReg.do) */
fun interface KcpCertRegisterPort {
	fun register(command: CertRegisterCommand): CertRegisterResult
}
