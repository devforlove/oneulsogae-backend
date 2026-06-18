package com.org.meeple.infra.popup.command.repository

import com.org.meeple.infra.popup.command.entity.PopupEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 팝업 영속성 엔티티에 대한 Spring Data JPA 리포지토리. (명령 경로 — 저장)
 * 명령 out-port는 [com.org.meeple.infra.popup.command.adapter.PopupAdapter]가 이 리포지토리로 구현한다.
 * 노출 팝업 조회는 [com.org.meeple.infra.popup.query.GetPopupDaoImpl]가 QueryDSL로 담당한다.
 */
interface PopupJpaRepository : JpaRepository<PopupEntity, Long>
