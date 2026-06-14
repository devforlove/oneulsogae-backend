plugins {
	id("meeple.kotlin-conventions")
}

dependencies {
	implementation(project(":meeple-core"))

	implementation("org.springframework.boot:spring-boot-starter-websocket")

	testImplementation("org.springframework.boot:spring-boot-starter-websocket-test")
}
