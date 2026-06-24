package com.org.meeple.api.chat

import com.org.meeple.common.chat.ChatRoomMatchType
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.infra.chat.command.entity.QChatRoomEntity
import com.org.meeple.infra.fixture.ChatRoomEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import io.kotest.matchers.shouldBe

/**
 * chat_rooms의 (match_type, match_id) 복합 유니크 검증 E2E.
 * solo_matches.id와 team_matches.id가 독립 시퀀스라 같은 값을 낼 수 있는데,
 * 예전엔 단일 컬럼 유니크(ux_match_id)라 같은 id의 solo·team 채팅방 생성이 duplicate key로 충돌했다.
 * match_type 판별 컬럼 + 복합 유니크로, 타입이 다르면 같은 match_id여도 각각 1방이 공존하고, 같은 (타입, id) 중복만 막힌다.
 */
class ChatRoomMatchNamespaceE2ETest : AbstractIntegrationSupport({

	describe("chat_rooms (match_type, match_id) 복합 유니크") {

		it("solo와 team 매치 id가 같아도 채팅방이 타입별로 각각 생성된다 (duplicate key 없음)") {
			val sharedMatchId = 777L

			IntegrationUtil.persist(ChatRoomEntityFixture.create(matchType = ChatRoomMatchType.SOLO, matchId = sharedMatchId))
			IntegrationUtil.persist(ChatRoomEntityFixture.create(matchType = ChatRoomMatchType.TEAM, matchId = sharedMatchId))

			roomCount(sharedMatchId) shouldBe 2L
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
	}
})

// 해당 match_id를 가진 채팅방 개수. (타입 무관 — 복합 유니크라 타입별로 1개씩 공존)
private fun roomCount(matchId: Long): Long {
	val room: QChatRoomEntity = QChatRoomEntity.chatRoomEntity
	return IntegrationUtil.getQuery()
		.select(room.count())
		.from(room)
		.where(room.matchId.eq(matchId))
		.fetchOne()!!
}
