package com.org.oneulsogae.infra.payments.command.entity

import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction

/**
 * payment_methods 테이블 영속성 엔티티. 체크아웃 화면에 노출할 결제수단을 [code]로 식별하는 참조 데이터다.
 * 활성 여부([active])·노출 순서([displayOrder])를 배포 없이 DB에서 조정한다. (앱은 읽기만, 행은 DB에서 관리)
 * 서버는 code에 로직을 두지 않으므로 enum 없이 문자열로 유지한다. 도메인 로직을 두지 않고 상태만 보관한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "payment_methods",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_payment_method_code", columnNames = ["code"]),
	],
)
class PaymentMethodEntity(
	/** 프론트가 참조하는 고유 코드. 예: "BANK_TRANSFER". */
	@Column(name = "code", nullable = false, length = 50)
	var code: String,

	/** 표시명. 예: "무통장입금". */
	@Column(name = "name", nullable = false, length = 100)
	var name: String,

	/** 노출 순서(오름차순). */
	@Column(name = "display_order", nullable = false)
	var displayOrder: Int,

	/** 활성 여부. 비활성 수단은 응답에서 제외된다. */
	@Column(name = "active", nullable = false)
	var active: Boolean,
) : BaseEntity()
