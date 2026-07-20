package com.org.oneulsogae.infra.notice.command.repository

import com.org.oneulsogae.infra.notice.command.entity.NoticeEntity
import org.springframework.data.jpa.repository.JpaRepository

interface NoticeJpaRepository : JpaRepository<NoticeEntity, Long>
