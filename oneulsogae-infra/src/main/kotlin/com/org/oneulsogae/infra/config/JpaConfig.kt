package com.org.oneulsogae.infra.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

/** @CreatedDate / @LastModifiedDate 자동 기록을 위한 JPA Auditing 활성화. */
@Configuration
@EnableJpaAuditing
class JpaConfig
