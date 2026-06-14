package com.org.meeple.core.chat.application.port.out

import com.org.meeple.core.chat.domain.ChatRoom
import com.org.meeple.core.chat.domain.ChatRoomSummary

/**
 * 채팅방 조회 아웃포트.
 * 도메인 모델([ChatRoom])/조회 결과([ChatRoomSummary])를 반환하며, 실제 구현은 infra 레이어의 어댑터가 담당한다.
 */
interface GetChatRoomPort {

	/** id로 채팅방을 조회한다. 없으면 null. (참가자 검증·상세 조회용) */
	fun findById(chatRoomId: Long): ChatRoom?

	/** 매칭 id로 채팅방을 조회한다. 없으면 null. (매칭당 1개이므로 단건, 멱등 생성 시 중복 방지용) */
	fun findByMatchId(matchId: Long): ChatRoom?

	/**
	 * 해당 사용자가 참가한 ACTIVE 상태의 채팅방을 상대 참가자들의 닉네임·프로필 이미지, 사용자 관점의 안 읽은 개수와 함께 조회한다.
	 * 참가 여부·안 읽은 개수는 참가자 단위([com.org.meeple.core.chat.domain.ChatRoomMember])로 관리되므로,
	 * 사용자의 참가자 행(user_id)에서 출발해 방을 찾고, 그 방들의 상대 참가자·프로필을 별도로 모아 채운다.
	 * (1:1이면 상대가 한 명, 그룹챗이면 여러 명이며, 참가자를 행으로 펼친 곱(중복행) 없이 방당 한 건으로 반환한다)
	 */
	fun findActiveByUserId(userId: Long): List<ChatRoomSummary>
}
