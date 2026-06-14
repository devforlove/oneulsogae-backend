package com.org.meeple.infra.config

import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * QueryDSL 설정. 동적/조인 조회용 [JPAQueryFactory]를 빈으로 등록해 어댑터에서 주입받아 쓴다.
 * (스프링이 관리하는 공유 [EntityManager] 프록시를 받아 현재 트랜잭션의 영속성 컨텍스트를 사용한다)
 */
@Configuration
class QuerydslConfig {

	@Bean
	fun jpaQueryFactory(entityManager: EntityManager): JPAQueryFactory =
		JPAQueryFactory(entityManager)
}
