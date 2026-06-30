package com.org.meeple.infra.fixture

import com.querydsl.core.types.EntityPath
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Table

/**
 * 통합테스트 DB 유틸 (QueryDSL).
 *
 * 테스트가 리포지토리에 직접 의존하지 않고 데이터 준비/조회/정리를 한곳에서 하도록 한다.
 * - [persist]: 엔티티 저장(영속화). QueryDSL [JPAQueryFactory]는 INSERT를 지원하지 않으므로 [EntityManager]로 저장한다.
 * - [getQuery]: 조회(select)용 QueryDSL 팩토리.
 * - [deleteAll]: 엔티티 경로의 모든 행 삭제.
 *
 * 정적 접근을 위해 빈 생성 시 [EntityManagerFactory]를 companion에 보관한다. (`@Import`로 컨텍스트에 등록)
 * infra의 testFixtures로 제공되며, api 모듈은 `testImplementation(testFixtures(project(":meeple-infra")))`로 가져간다.
 */
class IntegrationUtil(
	entityManagerFactory: EntityManagerFactory,
) {

	init {
		Companion.entityManagerFactory = entityManagerFactory
	}

	companion object {
		private lateinit var entityManagerFactory: EntityManagerFactory
		private val entityManager: EntityManager by lazy { entityManagerFactory.createEntityManager() }
		private val queryFactory: JPAQueryFactory by lazy { JPAQueryFactory(entityManager) }

		/** 엔티티를 저장(영속화)하고 식별자가 채워진 엔티티를 반환한다. */
		fun <T : Any> persist(entity: T): T {
			inTransaction {
				entityManager.persist(entity)
				entityManager.flush()
			}
			return entity
		}

		/** 조회용 QueryDSL 팩토리. (저장은 [persist], 삭제는 [deleteAll] 사용) */
		fun getQuery(): JPAQueryFactory = queryFactory

		/**
		 * QueryDSL 갱신(update/delete 등)을 트랜잭션 안에서 실행한다.
		 * 감사 컬럼(created_at 등)을 강제로 백데이트하는 등 테스트 데이터 보정에 쓴다. (일반 저장은 [persist])
		 */
		fun update(block: (JPAQueryFactory) -> Unit) {
			inTransaction { block(queryFactory) }
		}

		/**
		 * 해당 엔티티 경로의 모든 행을 삭제한다. (테스트 정리)
		 * JPQL bulk delete는 @SQLRestriction(소프트삭제 필터)이 적용돼 소프트삭제 행이 남으므로,
		 * @Table 이름을 알면 네이티브 DELETE로 소프트삭제 행까지 모두 지운다. (이름을 못 구하면 JPQL로 폴백)
		 */
		fun <T> deleteAll(path: EntityPath<T>) {
			val tableName: String? = path.type.getAnnotation(Table::class.java)?.name?.takeIf { it.isNotBlank() }
			inTransaction {
				if (tableName != null) {
					entityManager.createNativeQuery("delete from `$tableName`").executeUpdate()
				} else {
					queryFactory.delete(path).execute()
				}
			}
		}

		/**
		 * 네이티브 DML(UPDATE/DELETE)을 실행한다. (@SQLRestriction 우회 — 소프트삭제 행 조작 포함)
		 * 테스트 픽스처 보정(예: deleted_at 강제 변경)에 사용한다.
		 */
		fun nativeUpdate(sql: String) {
			inTransaction { entityManager.createNativeQuery(sql).executeUpdate() }
		}

		/**
		 * 네이티브 SQL로 단일 행을 조회한다. (@SQLRestriction 소프트삭제 필터를 우회해
		 * 소프트삭제·익명화 행을 직접 검증할 때 사용한다)
		 * - 결과가 없으면 null 반환.
		 * - 단일 컬럼이면 `arrayOf(value)`, 복수 컬럼이면 `Array<Any?>` 반환.
		 */
		fun nativeQuerySingleOrNull(sql: String): Array<Any?>? {
			var result: Array<Any?>? = null
			inTransaction {
				@Suppress("UNCHECKED_CAST")
				val rows: List<Any> = entityManager.createNativeQuery(sql).resultList as List<Any>
				if (rows.isNotEmpty()) {
					result = when (val row: Any = rows[0]) {
						is Array<*> -> @Suppress("UNCHECKED_CAST") (row as Array<Any?>)
						else -> arrayOf(row)
					}
				}
			}
			return result
		}

		// 단일 EntityManager를 공유하므로, 트랜잭션마다 영속성 컨텍스트를 비워(clear)
		// 서버가 커밋한 최신 상태를 이후 조회가 그대로 읽도록 한다. (1차 캐시로 인한 stale read 방지)
		private fun inTransaction(block: () -> Unit) {
			val transaction = entityManager.transaction
			transaction.begin()
			try {
				block()
				transaction.commit()
			} catch (e: Exception) {
				if (transaction.isActive) {
					transaction.rollback()
				}
				throw e
			} finally {
				entityManager.clear()
			}
		}
	}
}
