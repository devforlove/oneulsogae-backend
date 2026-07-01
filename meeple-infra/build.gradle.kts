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
	// GetMatchScoringProfileDaoImpl이 참조하는 MatchScoringProfile(순수 매칭 알고리즘) 의존.
	implementation(project(":meeple-matching"))

	api("org.springframework.boot:spring-boot-starter-data-jpa")
	// 매칭 풀 적재용. 스타터로 두어 Lettuce 클라이언트 + Spring Boot 자동설정(StringRedisTemplate)을 함께 가져온다.
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	// 분산 락 어댑터(RedissonDistributedLockAdapter)가 쓰는 RedissonClient 용.
	// Spring Boot 4와의 starter 호환을 피하려 코어 라이브러리로 둔다.
	implementation("org.redisson:redisson:3.50.0")
	runtimeOnly("com.mysql:mysql-connector-j")

	// QueryDSL: 엔티티로부터 Q클래스 생성 + 코어/JPA 타입.
	// Spring Boot 4(Jakarta 3.2/Hibernate 7) 호환을 위해 OpenFeign 포크를 사용한다.
	// 운영(api main)에 노출되지 않도록 implementation으로 둔다(소비자 컴파일 클래스패스에 비노출).
	implementation("io.github.openfeign.querydsl:querydsl-jpa:7.3.0")
	kapt("io.github.openfeign.querydsl:querydsl-apt:7.3.0:jakarta")

	// testFixtures(IntegrationUtil)가 QueryDSL을 쓰고, 이를 소비하는 api 테스트에도 노출되도록 testFixturesApi로 둔다.
	testFixturesApi("io.github.openfeign.querydsl:querydsl-jpa:7.3.0")
	// 엔티티 픽스처가 common의 enum(MatchType/MatchStatus/Gender/CoinUsageType)을 참조한다.
	testFixturesImplementation(project(":meeple-common"))

	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
}
