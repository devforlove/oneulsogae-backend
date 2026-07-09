package com.org.meeple.core.user.command.application.port.out

import com.org.meeple.core.user.command.domain.CertifiedIdentity

/**
 * KCP 본인확인 결과조회 아웃포트. 결과조회(getCertData.do) + 복호화 + KCP 필드 매핑까지 어댑터가 수행해
 * 검증된 신원([CertifiedIdentity])만 반환한다. (KCP JSON 세부는 infra에 은닉)
 */
fun interface KcpCertQueryPort {
	fun query(regCertKey: String, ordrIdxx: String): CertifiedIdentity
}
