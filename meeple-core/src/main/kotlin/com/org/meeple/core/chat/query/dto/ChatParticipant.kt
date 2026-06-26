package com.org.meeple.core.chat.query.dto

import com.org.meeple.common.user.Gender

/**
 * 채팅방 참가자 정보(read model).
 * 채팅방 목록/상세 조회에서 참가자의 식별·프로필 표시에 필요한 최소 정보(닉네임·프로필 이미지·성별)를 담는다.
 * 프로필 상세는 다른 도메인(user)이 소유하므로, 서비스/어댑터가 그 도메인의 데이터를 조회해 채운다.
 * 말풍선별 "안 읽은 사람 수" 클라이언트 계산을 위해 읽음 포인터([lastReadMessageId])와 활성여부([active])를 함께 담는다.
 * (나간 참가자는 [active]=false로, 카운트 대상에서 제외하도록 클라이언트가 판단한다)
 * [isMyTeam]은 TEAM 방에서 조회자와 같은 팀인지 여부다. 조회자 자신과 같은 팀원이 true, 상대 팀이 false다.
 * (SOLO 방은 팀이 없어 조회자 자신만 true다. 목록 조회는 상대 팀만 내려주므로 항상 false다)
 */
data class ChatParticipant(
	val userId: Long,
	val nickname: String?,
	val profileImageCode: String?,
	val gender: Gender?,
	val lastReadMessageId: Long?,
	val active: Boolean,
	val isMyTeam: Boolean,
)
