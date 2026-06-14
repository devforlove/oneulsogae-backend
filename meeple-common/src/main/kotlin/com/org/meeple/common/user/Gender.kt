package com.org.meeple.common.user

/** 성별. */
enum class Gender(val description: String) {
	MALE("남성"),
	FEMALE("여성"),

	;

	/** 반대 성별. (남녀 1:1 매칭에서 상대 후보 성별을 구할 때 사용) */
	fun opposite(): Gender = if (this == MALE) FEMALE else MALE
}
