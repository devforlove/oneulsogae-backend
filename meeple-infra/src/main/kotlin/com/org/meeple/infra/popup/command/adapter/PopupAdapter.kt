package com.org.meeple.infra.popup.command.adapter

import com.org.meeple.core.popup.command.application.port.out.HidePopupPort
import com.org.meeple.core.popup.command.application.port.out.SavePopupPort
import com.org.meeple.core.popup.command.domain.Popup
import com.org.meeple.infra.popup.command.entity.PopupEntity
import com.org.meeple.infra.popup.command.mapper.toDomain
import com.org.meeple.infra.popup.command.mapper.toEntity
import com.org.meeple.infra.popup.command.repository.PopupJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 팝업 명령 아웃포트([SavePopupPort]·[HidePopupPort])의 JPA 구현 어댑터. (명령 경로 — 엔티티당 어댑터 하나)
 * 엔티티/도메인 변환([com.org.meeple.infra.popup.command.mapper])을 책임지며, 외부에는 도메인 모델만 노출한다.
 * 노출 팝업 조회는 [com.org.meeple.infra.popup.query.GetPopupDaoImpl]가 담당한다.
 */
@Component
class PopupAdapter(
	private val popupJpaRepository: PopupJpaRepository,
) : SavePopupPort, HidePopupPort {

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(popup: Popup): Popup =
		popupJpaRepository.save(popup.toEntity()).toDomain()

	// 살아있는(미삭제) 대상만 조회해 soft-delete 시각을 찍어 저장한다. (@SQLRestriction이 이미 삭제된 행은 제외)
	override fun hideByIds(ids: List<Long>, now: LocalDateTime) {
		val targets: List<PopupEntity> = popupJpaRepository.findAllById(ids)
		targets.forEach { popup: PopupEntity -> popup.softDelete(now) }
		popupJpaRepository.saveAll(targets)
	}
}
