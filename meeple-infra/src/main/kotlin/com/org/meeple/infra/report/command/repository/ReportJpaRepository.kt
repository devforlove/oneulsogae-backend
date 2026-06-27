package com.org.meeple.infra.report.command.repository

import com.org.meeple.infra.report.command.entity.ReportEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ReportJpaRepository : JpaRepository<ReportEntity, Long>
