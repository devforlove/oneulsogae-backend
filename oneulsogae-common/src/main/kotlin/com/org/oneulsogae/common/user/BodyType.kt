package com.org.oneulsogae.common.user

/**
 * 체형. 값마다 어떤 성별의 체형인지를 [gender]로 구분한다.
 * (enum 클래스를 성별별로 나누지 않고, 하나의 타입 안에서 [gender] 필드로 분리한다.)
 */
enum class BodyType(val gender: Gender, val description: String) {

	FEMALE_SLIM(Gender.FEMALE, "슬림"),
	FEMALE_NORMAL(Gender.FEMALE, "보통"),
	FEMALE_GLAMOROUS(Gender.FEMALE, "글래머"),
	FEMALE_CHUBBY(Gender.FEMALE, "통통"),
	MALE_SLIM(Gender.MALE, "슬림"),
	MALE_NORMAL(Gender.MALE, "보통"),
	MALE_TONED(Gender.MALE, "탄탄한"),
	MALE_STURDY(Gender.MALE, "건장한"),
	MALE_CHUBBY(Gender.MALE, "통통"),
}
