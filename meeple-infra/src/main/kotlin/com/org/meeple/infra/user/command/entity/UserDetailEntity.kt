package com.org.meeple.infra.user.command.entity

import com.org.meeple.common.user.BodyType
import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus
import com.org.meeple.infra.common.BaseEntity
import com.org.meeple.infra.common.StringListJsonConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDate
import org.hibernate.annotations.SQLRestriction

/**
 * user_details 테이블 영속성 엔티티. 사용자(users)와 1:1로 연결되는 프로필 상세 정보를 보관한다.
 * OAuth 인증(ONBOARDING) 이후 정식 가입 과정에서 점진적으로 채워지므로 식별 컬럼을 제외한 대부분이 nullable이다.
 * 매칭에 쓰이는 속성 묶음이므로 match 도메인에서 소유한다. 도메인 로직을 두지 않고 상태만 보관한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "user_details",
	indexes = [
		Index(name = "ux_user_id", columnList = "user_id", unique = true),
	],
)
class UserDetailEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** 닉네임. 정식 가입(추가 정보 입력) 전에는 null. */
	@Column(name = "nickname")
	var nickname: String? = null,

	/** 랜덤 배정된 프로필 이미지 코드. (유저가 직접 등록하지 않고 코드에 따라 프로필이 정해진다) */
	@Column(name = "profile_image_code", length = 50)
	var profileImageCode: String? = null,

	/** 생년월일. */
	@Column(name = "birthday")
	var birthday: LocalDate? = null,

	/** 키(cm). */
	@Column(name = "height")
	var height: Int? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "gender", columnDefinition = "varchar(50)")
	var gender: Gender? = null,

	/** 휴대폰 번호. */
	@Column(name = "phone_number", length = 20)
	var phoneNumber: String? = null,

	/** 직업. */
	@Column(name = "job", length = 100)
	var job: String? = null,

	/** 활동 지역. */
	@Column(name = "activity_area", length = 100)
	var activityArea: String? = null,

	/** 활동 지역으로부터 산출한 권역 코드(1~5). */
	@Column(name = "region_code")
	var regionCode: Int? = null,

	/** 자기소개. */
	@Column(name = "introduction", length = 1000)
	var introduction: String? = null,

	/** 특성 목록(JSON). */
	@Convert(converter = StringListJsonConverter::class)
	@Column(name = "traits", columnDefinition = "json")
	var traits: List<String> = emptyList(),

	/** 관심사 목록(JSON). */
	@Convert(converter = StringListJsonConverter::class)
	@Column(name = "interests", columnDefinition = "json")
	var interests: List<String> = emptyList(),

	/** 회사 이메일. */
	@Column(name = "company_email")
	var companyEmail: String? = null,

	/** 회사명. */
	@Column(name = "company_name", length = 100)
	var companyName: String? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "marital_status", columnDefinition = "varchar(50)")
	var maritalStatus: MaritalStatus? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "smoking_status", columnDefinition = "varchar(50)")
	var smokingStatus: SmokingStatus? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "religion", columnDefinition = "varchar(50)")
	var religion: Religion? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "drinking_status", columnDefinition = "varchar(50)")
	var drinkingStatus: DrinkingStatus? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "body_type", columnDefinition = "varchar(50)")
	var bodyType: BodyType? = null,
) : BaseEntity()
