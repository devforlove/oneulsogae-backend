plugins {
	id("meeple.kotlin-conventions")
	id("org.springframework.boot")
}

dependencies {
	implementation(project(":meeple-common"))
	implementation(project(":meeple-core"))
	implementation(project(":meeple-infra"))
	implementation(project(":meeple-chatting"))
	implementation(project(":meeple-scheduler"))
	// 인증 검증 커널(TokenProvider/PrincipalDetails). 발급·로그인·SecurityConfig는 api가 이 모듈을 호출해 수행한다.
	implementation(project(":meeple-auth"))

	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

	// API 문서: springdoc-openapi (Swagger UI + OpenAPI 3). Spring Boot 4 지원 라인(3.0.x), Boot BOM 미관리라 버전 명시.
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")

	// 통합테스트: Testcontainers 2.x (버전은 Spring Boot BOM의 testcontainers-bom이 관리)
	// 2.0부터 모듈 좌표가 testcontainers-* 접두사로 변경됨
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-mysql")
	// Redis는 공식 모듈이 아니라 커뮤니티 모듈(com.redis). Spring Boot BOM이 버전 관리
	testImplementation("com.redis:testcontainers-redis")

	// 배치 통합테스트에서 배치가 적재한 Redis 풀 키를 정리하기 위한 StringRedisTemplate 컴파일 접근. (런타임 빈은 컨텍스트가 제공)
	testImplementation("org.springframework.boot:spring-boot-starter-data-redis")

	// 통합테스트: WireMock (외부 HTTP 의존성 스텁)
	testImplementation("org.wiremock:wiremock-standalone:3.13.1")

	// 통합테스트: Kotest (BDD 스펙 + Spring 연동)
	// kotest-extensions-spring 1.3.0이 kotest 5.8.1 API 기반이라 Kotest 6.x와는 비호환 → 5.9.x로 고정
	testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
	testImplementation("io.kotest:kotest-assertions-core:5.9.1")
	testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")

	// 통합테스트: infra의 testFixtures(IntegrationUtil + QueryDSL)를 테스트 의존성으로만 가져온다.
	testImplementation(testFixtures(project(":meeple-infra")))

	// 도메인 유닛 테스트: core의 testFixtures(도메인 모델 픽스처)를 테스트 의존성으로 가져온다.
	testImplementation(testFixtures(project(":meeple-core")))

	// E2E 테스트: RestAssured (실 서버 기동 후 HTTP 호출). Spring Boot 4 BOM이 관리하지 않아 버전 명시.
	testImplementation("io.rest-assured:rest-assured:6.0.0")
	testImplementation("io.rest-assured:kotlin-extensions:6.0.0")
}
