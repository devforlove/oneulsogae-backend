package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.core.user.command.domain.IdentityVerificationStatus
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.user.command.entity.QIdentityVerificationEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity

private val user: QUserEntity = QUserEntity.userEntity
private val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
private val identity: QIdentityVerificationEntity = QIdentityVerificationEntity.identityVerificationEntity

internal fun cleanupIdentity() {
	IntegrationUtil.deleteAll(identity)
	IntegrationUtil.deleteAll(detail)
	IntegrationUtil.deleteAll(user)
}

internal fun userStatusOfIdentity(userId: Long): UserStatus =
	IntegrationUtil.getQuery().select(user.status).from(user).where(user.id.eq(userId)).fetchOne()!!

internal fun latestIdentityStatusOf(userId: Long): IdentityVerificationStatus =
	IntegrationUtil.getQuery()
		.select(identity.status).from(identity)
		.where(identity.userId.eq(userId))
		.orderBy(identity.id.desc())
		.fetchFirst()!!
