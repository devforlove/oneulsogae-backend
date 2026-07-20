package com.org.oneulsogae.core.chat.query.dao

import com.org.oneulsogae.core.chat.query.dto.ChatRoomSummary
import com.org.oneulsogae.core.chat.query.dto.ChatRoomView

/**
 * 채팅방 조회 dao(query out-port 인터페이스).
 * 조회 결과 read model([ChatRoomSummary]/[ChatRoomView])을 반환하며, 실제 QueryDSL 구현은 infra 레이어의 어댑터가 담당한다.
 */
interface GetChatRoomDao {

	/**
	 * 해당 사용자가 참가한 ACTIVE 상태의 채팅방을 상대 참가자들의 닉네임·프로필 이미지, 사용자 관점의 안 읽은 개수와 함께 조회한다.
	 * 참가 여부·안 읽은 개수는 참가자 단위([com.org.oneulsogae.core.chat.command.domain.ChatRoomMember])로 관리되므로,
	 * 사용자의 참가자 행(user_id)에서 출발해 방을 찾고, 그 방들의 상대 참가자·프로필을 별도로 모아 채운다.
	 * (1:1이면 상대가 한 명, 그룹챗이면 여러 명이며, 참가자를 행으로 펼친 곱(중복행) 없이 방당 한 건으로 반환한다)
	 */
	fun findActiveByUserId(userId: Long): List<ChatRoomSummary>

	/**
	 * 채팅방의 식별·공통 상태를 단건 조회한다. 없으면 null. (상세 첫 페이지 헤더용)
	 * 변경 대상 로드용 단건 조회는 command 쪽 [com.org.oneulsogae.core.chat.command.application.port.out.GetChatRoomPort]가 따로 둔다. (adapter=메서드 쿼리 / dao=QueryDSL 분리)
	 */
	fun findById(chatRoomId: Long): ChatRoomView?
}
