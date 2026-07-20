package com.org.oneulsogae.common.config

import io.kotest.core.config.AbstractProjectConfig

/**
 * Kotest 프로젝트 설정.
 *
 * [allowOutOfOrderCallbacks]를 켜서 lifecycle 콜백(`afterEach` 등)을 테스트 정의보다 뒤(스펙 하단)에 둘 수 있게 한다.
 * 기본값(false)이면 "Cannot use afterTest after a test has been defined" 예외가 발생한다.
 *
 * Kotest 5는 클래스패스를 스캔해 [AbstractProjectConfig] 하위 클래스를 자동 탐지하므로,
 * `io.kotest.provided` 컨벤션 패키지가 아니어도 인식된다.
 */
class KotestProjectConfig : AbstractProjectConfig() {
	override var allowOutOfOrderCallbacks: Boolean? = true
}
