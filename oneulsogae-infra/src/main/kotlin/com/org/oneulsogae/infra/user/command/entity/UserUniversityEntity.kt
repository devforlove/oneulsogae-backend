package com.org.oneulsogae.infra.user.command.entity

import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction

/**
 * user_universities 테이블 영속성 엔티티.
 * 사용자가 입력한 학교 이메일의 도메인([emailDomain], 예: "snu.ac.kr")을 학교명([universityName])에 매핑한다.
 * 학교 이메일 입력 시 도메인으로 조회해 어떤 학교인지 식별하는 용도이며, 도메인은 학교당 유일하다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "user_universities",
	uniqueConstraints = [UniqueConstraint(name = "ux_email_domain", columnNames = ["email_domain"])],
)
class UserUniversityEntity(
	/** 학교 이메일의 도메인 부분. (예: "snu.ac.kr") */
	@Column(name = "email_domain", nullable = false, length = 255)
	var emailDomain: String,

	/** 도메인에 매핑되는 학교명. */
	@Column(name = "university_name", nullable = false, length = 100)
	var universityName: String,
) : BaseEntity()
