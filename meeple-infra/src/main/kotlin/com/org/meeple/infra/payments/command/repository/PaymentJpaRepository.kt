package com.org.meeple.infra.payments.command.repository

import com.org.meeple.infra.payments.command.entity.PaymentEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentJpaRepository : JpaRepository<PaymentEntity, Long>
