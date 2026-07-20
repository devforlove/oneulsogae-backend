package com.org.oneulsogae.infra.report.command.repository

import com.org.oneulsogae.infra.report.command.entity.ReportEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ReportJpaRepository : JpaRepository<ReportEntity, Long>
