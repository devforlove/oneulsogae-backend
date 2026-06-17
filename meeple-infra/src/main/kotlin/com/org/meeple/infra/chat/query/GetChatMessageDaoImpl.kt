package com.org.meeple.infra.chat.query

import com.org.meeple.core.chat.query.dao.GetChatMessageDao
import com.org.meeple.core.chat.query.dto.ChatMessageView
import com.org.meeple.core.chat.query.dto.ChatMessageViews
import com.org.meeple.infra.chat.command.entity.QChatMessageEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetChatMessageDao]의 QueryDSL 구현체.
 * (chat_room_id, id) 인덱스 역방향 스캔으로 최신부터 끊어 읽는 키셋 페이지네이션을 QueryDSL로 구현한다. (조회 전용)
 * 저장 out-port([com.org.meeple.core.chat.command.application.port.out.SaveChatMessagePort])는 [ChatMessageAdapter]가 메서드 쿼리로 따로 구현한다.
 */
@Component
class GetChatMessageDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetChatMessageDao {

	/**
	 * 최신 [limit]건을 id 내림차순으로 끊어 읽어 [ChatMessageViews]로 반환한다. (일급 컬렉션이 id 오름차순으로 보정)
	 * [beforeMessageId]가 있으면 그 id보다 과거(더 작은 id) 구간만 잇는다. (커서 조건은 null이면 무시돼 첫 페이지가 된다)
	 */
	override fun findByChatRoom(chatRoomId: Long, beforeMessageId: Long?, limit: Int): ChatMessageViews {
		val message: QChatMessageEntity = QChatMessageEntity.chatMessageEntity
		val views: List<ChatMessageView> = queryFactory
			.select(
				Projections.constructor(
					ChatMessageView::class.java,
					message.id,
					message.senderId,
					message.content,
					message.type,
					message.sentAt,
				),
			)
			.from(message)
			.where(
				message.chatRoomId.eq(chatRoomId),
				beforeMessageId?.let { message.id.lt(it) },
			)
			.orderBy(message.id.desc())
			.limit(limit.toLong())
			.fetch()
		return ChatMessageViews(views)
	}
}
