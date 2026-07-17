plugins {
	id("meeple.kotlin-conventions")
	// QueryDSL Q클래스 생성을 위한 KAPT (Kotlin @Entity 어노테이션 프로세싱)
	kotlin("kapt")
	// 통합테스트용 IntegrationUtil을 testFixtures로 노출 → api가 testImplementation(testFixtures(...))로 소비
	`java-test-fixtures`
}

dependencies {
	implementation(project(":meeple-common"))
	implementation(project(":meeple-core"))
	// 배치 전용 포트(scheduler 소유)를 infra 어댑터가 구현하므로 의존한다. (infra -> scheduler)
	implementation(project(":meeple-scheduler"))
	// 채팅 전용 포트(chatting 소유)를 infra 어댑터가 구현하므로 의존한다. (infra -> chatting)
	implementation(project(":meeple-chatting"))
	// 어드민 전용 포트(admin 소유)를 infra 어댑터가 구현하므로 의존한다. (infra -> admin)
	implementation(project(":meeple-admin"))

	api("org.springframework.boot:spring-boot-starter-data-jpa")
	// KCP 거래등록/결과조회 HTTP 어댑터용 RestClient. (Boot BOM이 버전 관리)
	implementation("org.springframework:spring-web")
	// StubPaymentGatewayAdapter가 RequestContextHolder로 현재 요청의 HttpServletRequest를 읽는다.
	// 실제 서블릿 컨테이너는 최종 애플리케이션(meeple-api의 spring-boot-starter-webmvc)이 런타임에 제공하므로 compileOnly로 둔다.
	compileOnly("jakarta.servlet:jakarta.servlet-api")
	// KCP 본인확인 V2 암복호화 라이브러리(utils.Crypto: encryptJson/decryptJson). Maven 미배포 → 로컬 JAR.
	// JDK(javax.crypto/Base64)만 의존하므로 추가 전이 의존성 없음. (KcpCertCryptoPort 실구현이 사용)
	implementation(files("libs/crypto-1.0.0.jar"))
	// 매칭 풀 적재용. 스타터로 두어 Lettuce 클라이언트 + Spring Boot 자동설정(StringRedisTemplate)을 함께 가져온다.
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	// 분산 락 어댑터(RedissonDistributedLockAdapter)가 쓰는 RedissonClient 용.
	// Spring Boot 4와의 starter 호환을 피하려 코어 라이브러리로 둔다.
	implementation("org.redisson:redisson:3.50.0")
	runtimeOnly("com.mysql:mysql-connector-j")

	// AWS S3(파일 스토리지) 클라이언트. 로컬은 LocalStack, 운영은 실제 S3에 접속한다.
	// Spring Boot BOM이 AWS SDK를 관리하지 않아 자체 BOM으로 버전을 정렬한다.
	implementation(platform("software.amazon.awssdk:bom:2.46.21"))
	implementation("software.amazon.awssdk:s3")
	// SES(이메일 인증번호 발송) 클라이언트. prod 프로파일에서만 빈이 활성화된다(local·test는 로깅 스텁).
	implementation("software.amazon.awssdk:sesv2")
	// 동기 S3 클라이언트용 경량 HTTP 구현(JDK HttpURLConnection 기반). 명시하지 않으면 런타임에 HTTP 구현을 못 찾는다.
	implementation("software.amazon.awssdk:url-connection-client")

	// QueryDSL: 엔티티로부터 Q클래스 생성 + 코어/JPA 타입.
	// Spring Boot 4(Jakarta 3.2/Hibernate 7) 호환을 위해 OpenFeign 포크를 사용한다.
	// 운영(api main)에 노출되지 않도록 implementation으로 둔다(소비자 컴파일 클래스패스에 비노출).
	implementation("io.github.openfeign.querydsl:querydsl-jpa:7.3.0")
	kapt("io.github.openfeign.querydsl:querydsl-apt:7.3.0:jakarta")

	// testFixtures(IntegrationUtil)가 QueryDSL을 쓰고, 이를 소비하는 api 테스트에도 노출되도록 testFixturesApi로 둔다.
	testFixturesApi("io.github.openfeign.querydsl:querydsl-jpa:7.3.0")
	// 엔티티 픽스처가 common의 enum(MatchType/MatchStatus/Gender/CoinUsageType)을 참조한다.
	testFixturesImplementation(project(":meeple-common"))
	// IdentityVerificationEntityFixture가 core의 IdentityVerificationStatus를 참조한다.
	testFixturesImplementation(project(":meeple-core"))

	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
}

