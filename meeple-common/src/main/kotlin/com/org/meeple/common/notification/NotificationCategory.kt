package com.org.meeple.common.notification

/**
 * 알림 설정 카테고리. 마이탭 토글(push 마스터 제외 5종)과 1:1 대응한다.
 * AlarmType은 [com.org.meeple.common.alarm.AlarmType.category]로 이 카테고리에 묶인다.
 * MESSAGE·MARKETING은 현재 대응하는 AlarmType이 없는 예약 슬롯이다(채팅·마케팅 알림톡 추가 시 사용).
 * COIN은 인앱 전용(알림톡 push 없음)이라 마이탭 토글이 없다. NotificationPreference.allows가 항상 false로 게이트한다.
 */
enum class NotificationCategory {
	ONE_TO_ONE,
	MEETING,
	TEAM,
	MESSAGE,
	MARKETING,
	COIN,
}
