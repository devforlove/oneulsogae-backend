package com.org.meeple.infra.fixture

import com.org.meeple.infra.auth.entity.RefreshTokenEntity
import java.time.LocalDateTime

/** [RefreshTokenEntity] 테스트 픽스처. tokenId는 ux_token_id 유니크 제약이 있어 여러 개 만들 땐 달리한다. */
object RefreshTokenEntityFixture {

	fun create(
		userId: Long = 1L,
		tokenId: String = "test-token-id",
		expiresAt: LocalDateTime = LocalDateTime.now().plusDays(14),
		revoked: Boolean = false,
	): RefreshTokenEntity =
		RefreshTokenEntity(
			tokenId = tokenId,
			userId = userId,
			expiresAt = expiresAt,
			revoked = revoked,
		)
}
