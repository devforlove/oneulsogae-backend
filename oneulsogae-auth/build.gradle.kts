plugins {
	id("oneulsogae.kotlin-conventions")
}

dependencies {
	// 인증 검증 커널(JWT 검증·파싱 + Principal)만 둔다. 발급/로그인/쿠키/SecurityConfig는 소비 모듈(oneulsogae-api)에 남는다.
	// 추후 oneulsogae-chatting을 별도 인스턴스로 분리할 때 이 모듈만 의존해 CONNECT 토큰을 검증할 수 있다.

	// 공개 시그니처에 노출(Authentication, GrantedAuthority, OAuth2User)되므로 api로 전이 노출한다.
	api("org.springframework.security:spring-security-core")
	api("org.springframework.security:spring-security-oauth2-core")

	// @Component / @Value 사용.
	implementation("org.springframework:spring-context")

	// JWT 검증/파싱은 이 모듈 내부 구현 세부라 implementation/runtimeOnly로 가둔다.
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
}
