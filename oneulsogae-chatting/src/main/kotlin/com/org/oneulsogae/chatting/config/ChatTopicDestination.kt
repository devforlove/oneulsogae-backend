package com.org.oneulsogae.chatting.config

/**
 * 방 브로드캐스트 구독 목적지 `/topic/{chatRoomId}`.
 * STOMP destination 문자열에서 "방 토픽 여부"와 chatRoomId 파싱을 한곳에 응집한다. (인터셉터의 구독 인가 분기에서 쓴다)
 */
@JvmInline
value class ChatTopicDestination(private val destination: String) {

	/** `/topic/`으로 시작하는 방 브로드캐스트 목적지인지. (아니면 인가 대상이 아니다) */
	val isRoomTopic: Boolean
		get() = destination.startsWith(PREFIX)

	/** 방 토픽일 때의 chatRoomId. 숫자로 파싱되지 않으면 null. */
	fun chatRoomIdOrNull(): Long? =
		destination.removePrefix(PREFIX).substringBefore('/').toLongOrNull()

	companion object {
		private const val PREFIX = "/topic/"
	}
}
