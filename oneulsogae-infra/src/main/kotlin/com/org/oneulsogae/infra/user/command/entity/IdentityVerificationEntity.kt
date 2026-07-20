package com.org.oneulsogae.infra.user.command.entity

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.user.command.domain.IdentityVerificationStatus
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "identity_verifications",
	indexes = [
		Index(name = "idx_iv_user_id", columnList = "user_id"),
		Index(name = "idx_iv_phone_number", columnList = "phone_number"),
	],
)
class IdentityVerificationEntity(
	@Column(name = "user_id", nullable = false)
	val userId: Long,

	@Column(name = "ordr_idxx", nullable = false, length = 50)
	val ordrIdxx: String,

	@Column(name = "reg_cert_key", nullable = false, length = 100)
	val regCertKey: String,

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	var status: IdentityVerificationStatus,

	@Column(name = "real_name")
	var realName: String? = null,

	@Column(name = "birthday")
	var birthday: LocalDate? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "gender", columnDefinition = "varchar(20)")
	var gender: Gender? = null,

	@Column(name = "phone_number", length = 20)
	var phoneNumber: String? = null,

	@Column(name = "di", length = 100)
	var di: String? = null,

	@Column(name = "ci_encrypted", length = 512)
	var ciEncrypted: String? = null,

	@Column(name = "foreigner")
	var foreigner: Boolean? = null,

	@Column(name = "telecom", length = 20)
	var telecom: String? = null,

	@Column(name = "verified_at")
	var verifiedAt: LocalDateTime? = null,
) : BaseEntity() {

	/** 파기(탈퇴 유예 경과) 시 개인정보를 제거한다. 거래 추적용 ordr_idxx/reg_cert_key(불변)는 남긴다. */
	fun anonymize() {
		realName = null
		birthday = null
		gender = null
		phoneNumber = null
		di = null
		ciEncrypted = null
		foreigner = null
		telecom = null
		verifiedAt = null
	}
}
