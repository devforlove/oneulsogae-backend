plugins {
	id("meeple.kotlin-conventions")
}

dependencies {
	// chatting은 core에 의존하지 않는다. 채팅에 필요한 도메인/포트/서비스를 자체 보유하고, 공용 enum만 common에서 가져온다.
	// 영속성은 자기 out-port를 infra 어댑터가 구현해 채운다. (scheduler 모듈과 동일한 자립 구조)
	implementation(project(":meeple-common"))
	// CONNECT 토큰 검증용 인증 커널(TokenProvider/PrincipalDetails). 발급/로그인은 의존하지 않는다.
	implementation(project(":meeple-auth"))

	// 발송 서비스의 트랜잭션 경계(@Transactional)용. 실제 트랜잭션 매니저는 구동 앱(infra/JPA)이 제공한다.
	implementation("org.springframework:spring-tx")
	// 세션 이벤트 로깅용. (구현체 logback은 구동 앱이 제공)
	implementation("org.slf4j:slf4j-api")

	implementation("org.springframework.boot:spring-boot-starter-websocket")

	testImplementation("org.springframework.boot:spring-boot-starter-websocket-test")
}
