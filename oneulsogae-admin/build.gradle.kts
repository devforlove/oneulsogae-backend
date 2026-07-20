plugins {
	id("oneulsogae.kotlin-conventions")
}

dependencies {
	// admin은 core에 의존하지 않는다. 어드민의 도메인/유스케이스/서비스/포트를 자체 보유하고, 공용 enum만 common에서 가져온다.
	// 영속성은 자기 out-port(Dao)를 infra 어댑터가 구현해 채운다. (chatting·scheduler와 동일한 자립 구조)
	implementation(project(":oneulsogae-common"))

	// @Service(스테레오타입) · @Transactional 경계용. 실제 트랜잭션 매니저는 구동 앱(infra/JPA)이 제공한다.
	implementation("org.springframework:spring-context")
	implementation("org.springframework:spring-tx")
	// AdminErrorCode가 HttpStatus를 담는다. (core가 ErrorCode에 HttpStatus를 쓰는 것과 동일 — core 의존이 아니라 spring-web 라이브러리 의존)
	implementation("org.springframework:spring-web")
	// AdminExceptionHandler 로깅용. (구현체는 구동 앱이 제공, 여기선 API만 컴파일 타임에 필요)
	implementation("org.slf4j:slf4j-api")
}
