plugins {
	`java-library`
	kotlin("jvm")
	kotlin("plugin.spring")
	kotlin("plugin.jpa")
	id("io.spring.dependency-management")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.6")
	}
}

dependencies {
	"implementation"("org.jetbrains.kotlin:kotlin-reflect")
	"implementation"("tools.jackson.module:jackson-module-kotlin")
	"testImplementation"("org.jetbrains.kotlin:kotlin-test-junit5")
	"testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
	jvmToolchain(21)
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
