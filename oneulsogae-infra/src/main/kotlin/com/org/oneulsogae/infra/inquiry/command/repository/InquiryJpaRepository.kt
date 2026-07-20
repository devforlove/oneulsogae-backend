package com.org.oneulsogae.infra.inquiry.command.repository

import com.org.oneulsogae.infra.inquiry.command.entity.InquiryEntity
import org.springframework.data.jpa.repository.JpaRepository

interface InquiryJpaRepository : JpaRepository<InquiryEntity, Long>
