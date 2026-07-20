package com.org.oneulsogae.common.config

import com.org.oneulsogae.core.user.command.application.port.out.CertRegisterResult
import com.org.oneulsogae.core.user.command.application.port.out.KcpCertQueryPort
import com.org.oneulsogae.core.user.command.application.port.out.KcpCertRegisterPort
import com.org.oneulsogae.core.user.command.domain.CertifiedIdentity
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * 통합 테스트에서 KCP 아웃포트를 페이크로 대체한다. (실 HTTP·암호화 미호출)
 * - register: 결정적 regCertKey/callUrl 반환.
 * - query: [FakeKcpCertData.next]에 세팅한 검증값을 반환(테스트가 성인/미성년/DI를 제어).
 * [AbstractIntegrationSupport]에 등록돼 모든 통합 테스트가 단일 컨텍스트를 공유한다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestKcpConfig {

	@Bean
	@Primary
	fun fakeKcpCertRegisterPort(): KcpCertRegisterPort =
		KcpCertRegisterPort { command ->
			CertRegisterResult(
				regCertKey = "TEST-REG-${command.ordrIdxx}",
				callUrl = "https://testcert.kcp.co.kr/cert?regCertKey=TEST-REG-${command.ordrIdxx}",
			)
		}

	@Bean
	@Primary
	fun fakeKcpCertQueryPort(): KcpCertQueryPort =
		KcpCertQueryPort { _, _ ->
			FakeKcpCertData.next ?: error("FakeKcpCertData.next 미설정 — 테스트에서 먼저 세팅하세요.")
		}
}

object FakeKcpCertData {
	var next: CertifiedIdentity? = null
}
