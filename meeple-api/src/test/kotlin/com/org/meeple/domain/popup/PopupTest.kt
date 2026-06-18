package com.org.meeple.domain.popup

import com.org.meeple.core.fixture.PopupFixture
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [com.org.meeple.core.popup.command.domain.Popup] 도메인 유닛 테스트.
 * 프레임워크·인프라 없이 노출 여부 판정(isVisible) 로직을 검증한다. 시각은 파라미터로 주입한다.
 * 팝업 인스턴스는 core testFixtures의 [PopupFixture]로 만든다.
 */
class PopupTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 6, 11, 12, 0)

	describe("isVisible") {
		it("기간 제한이 없으면 노출 대상이다") {
			PopupFixture.create().isVisible(now) shouldBe true
		}

		it("노출 시작 전이면 노출 대상이 아니다") {
			PopupFixture.create(exposedFrom = now.plusMinutes(1)).isVisible(now) shouldBe false
		}

		it("노출 종료 후면 노출 대상이 아니다") {
			PopupFixture.create(exposedTo = now.minusMinutes(1)).isVisible(now) shouldBe false
		}

		it("노출 기간 안(경계 포함)이면 노출 대상이다") {
			PopupFixture.create(exposedFrom = now, exposedTo = now).isVisible(now) shouldBe true
			PopupFixture.create(exposedFrom = now.minusHours(1), exposedTo = now.plusHours(1)).isVisible(now) shouldBe true
		}
	}
})
