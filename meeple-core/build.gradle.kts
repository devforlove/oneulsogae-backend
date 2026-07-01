plugins {
	id("meeple.kotlin-conventions")
	`java-test-fixtures`
}

dependencies {
	implementation(project(":meeple-common"))
	// 매칭 스코어링·선택 알고리즘(순수). 일일 배치(scheduler)와 실시간 추가 소개(core)가 공유한다.
	implementation(project(":meeple-matching"))

	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework:spring-tx")
	implementation("org.springframework:spring-web")
	// @DistributedLock AOP 위빙(@Aspect/@Around)을 위한 aspectjweaver + @EnableAspectJAutoProxy 자동설정.
	// 락 동작 자체는 core의 out-port(DistributedLockPort)로 추상화하고 구현은 infra(Redisson)에 둔다.
	implementation("org.springframework.boot:spring-boot-starter-aspectj")

	// 도메인 유닛 테스트는 meeple-api 테스트 소스셋(com.org.meeple.domain.* 패키지)으로 옮겨 통합테스트와 함께 구동한다.
	// 따라서 core 모듈 자체에는 testImplementation 의존성을 두지 않는다.
	// 다만 도메인 모델 픽스처는 testFixtures로 노출 → api가 testImplementation(testFixtures(...))로 소비한다.
	// 픽스처가 common의 enum(Gender/MatchType/ChatRoomStatus 등)을 참조하므로 testFixtures 클래스패스에 common을 둔다.
	testFixturesImplementation(project(":meeple-common"))
}
