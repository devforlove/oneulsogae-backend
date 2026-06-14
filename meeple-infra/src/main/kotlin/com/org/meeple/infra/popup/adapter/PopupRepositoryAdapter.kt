package com.org.meeple.infra.popup.adapter

import com.org.meeple.core.popup.application.port.out.GetPopupPort
import com.org.meeple.core.popup.application.port.out.SavePopupPort
import com.org.meeple.core.popup.domain.Popup
import com.org.meeple.core.popup.domain.Popups
import com.org.meeple.infra.popup.entity.PopupEntity
import com.org.meeple.infra.popup.mapper.toDomain
import com.org.meeple.infra.popup.mapper.toEntity
import com.org.meeple.infra.popup.repository.PopupJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 팝업 아웃포트([GetPopupPort], [SavePopupPort])의 JPA 구현 어댑터.
 * 엔티티/도메인 변환([PopupMapper])을 책임지며, 외부에는 도메인 모델만 노출한다.
 */
@Component
class PopupRepositoryAdapter(
	private val popupJpaRepository: PopupJpaRepository,
) : GetPopupPort, SavePopupPort {

	override fun findById(id: Long): Popup? =
		popupJpaRepository.findById(id).orElse(null)?.toDomain()

	override fun findVisible(now: LocalDateTime): Popups =
		Popups(popupJpaRepository.findVisible(now).map { entity: PopupEntity -> entity.toDomain() })

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(popup: Popup): Popup =
		popupJpaRepository.save(popup.toEntity()).toDomain()
}
