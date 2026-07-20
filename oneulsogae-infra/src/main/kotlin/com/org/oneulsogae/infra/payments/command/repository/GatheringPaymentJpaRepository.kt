package com.org.oneulsogae.infra.payments.command.repository

import com.org.oneulsogae.infra.payments.command.entity.GatheringPaymentEntity
import org.springframework.data.jpa.repository.JpaRepository

interface GatheringPaymentJpaRepository : JpaRepository<GatheringPaymentEntity, Long>
