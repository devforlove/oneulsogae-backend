package com.org.meeple.infra.user.command.entity

import com.org.meeple.common.user.DistancePreference
import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * user_ideal_types 테이블 영속성 엔티티. 사용자(users)와 1:1로 연결되는 이상형(매칭 선호)을 보관한다.
 * 나이/키는 숫자 경계로, 나머지는 enum으로 저장한다(모두 nullable, null = "상관없음"). 도메인 로직은 두지 않는다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "user_ideal_types",
	indexes = [
		Index(name = "ux_user_ideal_type_user_id", columnList = "user_id", unique = true),
	],
)
class UserIdealTypeEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** 선호 나이 하한. */
	@Column(name = "age_min")
	var ageMin: Int? = null,

	/** 선호 나이 상한. */
	@Column(name = "age_max")
	var ageMax: Int? = null,

	/** 선호 키(cm) 하한. */
	@Column(name = "height_min")
	var heightMin: Int? = null,

	/** 선호 키(cm) 상한. */
	@Column(name = "height_max")
	var heightMax: Int? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "marital_status", columnDefinition = "varchar(50)")
	var maritalStatus: MaritalStatus? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "smoking_status", columnDefinition = "varchar(50)")
	var smokingStatus: SmokingStatus? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "drinking_status", columnDefinition = "varchar(50)")
	var drinkingStatus: DrinkingStatus? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "religion", columnDefinition = "varchar(50)")
	var religion: Religion? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "distance", columnDefinition = "varchar(50)")
	var distance: DistancePreference? = null,
) : BaseEntity()
