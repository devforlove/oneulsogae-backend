package com.org.meeple.api.user

import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.coin.command.entity.QCoinBalanceEntity
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.UserDetailEntity
import com.org.meeple.infra.user.command.entity.QCompanyEmailVerificationEntity
import com.org.meeple.infra.user.command.entity.QUserCompanyEntity
import com.org.meeple.infra.user.command.entity.QUserEntity

/**
 * 온보딩 엔드포인트 E2E 공통 헬퍼.
 *
 * 각 스펙은 `AbstractIntegrationSupport({ ... })` 생성자 람다 스타일로 작성하고, `afterTest`에서 [cleanupOnboarding]을 호출한다.
 * 서버가 커밋한 최신 상태를 읽는 QueryDSL 조회 헬퍼를 같은 패키지의 스펙들이 공유한다.
 */

/** 온보딩 관련 테이블 전체 정리. (한 테스트가 일부만 건드려도 전체를 지워 격리한다. 빈 테이블 삭제는 무해) */
internal fun cleanupOnboarding() {
	IntegrationUtil.deleteAll(QCompanyEmailVerificationEntity.companyEmailVerificationEntity)
	IntegrationUtil.deleteAll(QUserCompanyEntity.userCompanyEntity)
	IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
	IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
	IntegrationUtil.deleteAll(QUserEntity.userEntity)
}

/** 해당 사용자의 코인 잔액 행 개수. (온보딩 시 잔액 행이 준비되는지 확인용) */
internal fun coinBalanceCountOf(userId: Long): Int =
	IntegrationUtil.getQuery()
		.selectFrom(QCoinBalanceEntity.coinBalanceEntity)
		.where(QCoinBalanceEntity.coinBalanceEntity.userId.eq(userId))
		.fetch()
		.size

internal fun userStatusOf(userId: Long): UserStatus =
	IntegrationUtil.getQuery()
		.select(QUserEntity.userEntity.status)
		.from(QUserEntity.userEntity)
		.where(QUserEntity.userEntity.id.eq(userId))
		.fetchOne()!!

internal fun userDetailOf(userId: Long): UserDetailEntity =
	IntegrationUtil.getQuery()
		.selectFrom(QUserDetailEntity.userDetailEntity)
		.where(QUserDetailEntity.userDetailEntity.userId.eq(userId))
		.fetchOne()!!

internal fun verificationCountOf(userId: Long): Int =
	IntegrationUtil.getQuery()
		.selectFrom(QCompanyEmailVerificationEntity.companyEmailVerificationEntity)
		.where(QCompanyEmailVerificationEntity.companyEmailVerificationEntity.userId.eq(userId))
		.fetch()
		.size
