package com.org.meeple.core.chat.query.dao

import com.org.meeple.core.chat.query.dto.ChatParticipants

/**
 * 채팅방 참가자(프로필 포함) 조회 리포지토리(query out-port 인터페이스).
 * 참가 검증과 프로필 표시에 필요한 정보를 프로필 조인으로 한 번에 가져온다. (1+N 방지)
 * 조인이 필요한 조회이므로 QueryDSL 어댑터([com.org.meeple.infra.chat.query.GetChatParticipantDaoImpl])가 구현한다.
 */
interface GetChatParticipantDao {

	/** 한 채팅방의 참가자 전체를 프로필(닉네임·이미지·성별)과 함께 조회한다. (나간 참가자도 포함) */
	fun findByChatRoomId(chatRoomId: Long): ChatParticipants
}
