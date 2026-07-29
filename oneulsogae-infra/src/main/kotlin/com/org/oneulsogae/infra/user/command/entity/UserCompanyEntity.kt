package com.org.oneulsogae.infra.user.command.entity

import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction

/**
 * user_companies 테이블 영속성 엔티티.
 * 사용자가 입력한 회사 이메일의 도메인([emailDomain], 예: "oneulsogae.com")을 회사명([companyName])에 매핑한다.
 * 그룹 계열사처럼 한 도메인을 여러 회사가 공유할 수 있어 도메인:회사는 1:N이며, (도메인, 회사명) 조합만 유일하다.
 * 후보가 여럿이면 인증 요청 시 사용자가 회사를 선택한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "user_companies",
	uniqueConstraints = [
		// 선두 컬럼(email_domain)이 도메인 후보 조회 seek도 겸한다. (별도 단독 인덱스 불필요)
		UniqueConstraint(name = "ux_email_domain_company_name", columnNames = ["email_domain", "company_name"]),
	],
)
class UserCompanyEntity(
	/** 회사 이메일의 도메인 부분. (예: "oneulsogae.com") */
	@Column(name = "email_domain", nullable = false, length = 255)
	var emailDomain: String,

	/** 도메인에 매핑되는 회사명. */
	@Column(name = "company_name", nullable = false, length = 100)
	var companyName: String,
) : BaseEntity()
