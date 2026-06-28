package com.org.meeple.infra.notice.command.repository

import com.org.meeple.infra.notice.command.entity.NoticeEntity
import org.springframework.data.jpa.repository.JpaRepository

interface NoticeJpaRepository : JpaRepository<NoticeEntity, Long>
