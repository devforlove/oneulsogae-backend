package com.org.meeple.common.chat

/**
 * 채팅방이 어떤 매칭에서 생성됐는지를 나타내는 판별값.
 * chat_rooms.match_id는 1:1(solo) 매칭과 2:2(team) 매칭의 id를 함께 가리키는 다형성 참조라,
 * 두 시퀀스가 같은 id를 내면 구분할 수 없다. 이 타입으로 출처를 구분해
 * (match_type, match_id) 복합 유니크로 타입별 1방을 보장하고, 나가기/정리 시 올바른 매칭 도메인으로 라우팅한다.
 */
enum class ChatRoomMatchType {

	/** 1:1(남녀) 매칭에서 생성된 채팅방. (solo_matches.id) */
	SOLO,

	/** 2:2(팀) 매칭에서 생성된 채팅방. (team_matches.id) */
	TEAM,
}
