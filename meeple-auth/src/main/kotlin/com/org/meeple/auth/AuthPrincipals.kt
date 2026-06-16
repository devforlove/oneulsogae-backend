package com.org.meeple.auth

import org.springframework.security.core.Authentication
import java.security.Principal

/**
 * 인증 주체(Principal)에서 userId를 꺼낸다.
 * 인증되지 않았거나(또는 [PrincipalDetails]가 아니면) null. (STOMP `accessor.user`, SecurityContext 등 Principal을 다루는 곳 공통)
 */
fun Principal?.userIdOrNull(): Long? =
	((this as? Authentication)?.principal as? PrincipalDetails)?.id
