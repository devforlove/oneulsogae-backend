package com.org.meeple.common.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.mysql.MySQLContainer

/**
 * 통합테스트용 MySQL Testcontainer 설정.
 *
 * `@ServiceConnection`이 컨테이너의 접속 정보를 datasource 프로퍼티로 자동 연결하므로,
 * 별도의 `@DynamicPropertySource` 없이도 Spring 컨텍스트가 컨테이너 DB에 연결된다.
 * (Testcontainers 2.x에서 MySQL 모듈 클래스는 `org.testcontainers.mysql.MySQLContainer` 비제네릭 구상 타입)
 */
@TestConfiguration(proxyBeanMethods = false)
class TestDatabaseContainersConfig {

	@Bean
	@ServiceConnection
	fun mysql(): MySQLContainer =
		MySQLContainer("mysql:8.4.0")
			.withDatabaseName("testdb")
			.withUsername("test")
			.withPassword("test")
}
