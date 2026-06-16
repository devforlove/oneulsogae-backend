package com.org.meeple.infra.user.command.entity

import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction

/**
 * user_companies 테이블 영속성 엔티티.
 * 사용자가 입력한 회사 이메일의 도메인([emailDomain], 예: "meeple.com")을 회사명([companyName])에 매핑한다.
 * 회사 이메일 입력 시 도메인으로 조회해 어떤 회사인지 식별하는 용도이며, 도메인은 회사당 유일하다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "user_companies",
	uniqueConstraints = [UniqueConstraint(name = "udx_email_domain", columnNames = ["email_domain"])],
)
class UserCompanyEntity(
	/** 회사 이메일의 도메인 부분. (예: "meeple.com") */
	@Column(name = "email_domain", nullable = false, length = 255)
	var emailDomain: String,

	/** 도메인에 매핑되는 회사명. */
	@Column(name = "company_name", nullable = false, length = 100)
	var companyName: String,
) : BaseEntity()
