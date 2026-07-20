package com.org.oneulsogae.infra.payments.command.repository

import com.org.oneulsogae.infra.payments.command.entity.CoinPaymentEntity
import org.springframework.data.jpa.repository.JpaRepository

interface CoinPaymentJpaRepository : JpaRepository<CoinPaymentEntity, Long>
