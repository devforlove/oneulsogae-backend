package com.org.oneulsogae.api.offline

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import org.hamcrest.Matchers.containsString

/**
 * 소개 이미지 공개 프록시 `GET /images/{*key}` E2E 테스트.
 * - gathering-descriptions/ 프리픽스 key만 302(Location: presigned URL)로 리다이렉트한다.
 * - 그 외 프리픽스, 경로 조작(..)이 섞인 key는 404.
 * (presigned URL은 TestFileStorageConfig의 페이크로 대체 — https://presigned.test/<imageKey>)
 */
class GatheringDescriptionImageProxyE2ETest : AbstractIntegrationSupport({

	describe("GET /images/{*key}") {

		it("소개 이미지 프리픽스 key는 비로그인도 302로 리다이렉트하고 Location에 presigned URL을 싣는다") {
			get("/images/gathering-descriptions/x.jpg") {
				noRedirect()
			} expect {
				status(302)
				header("Location", containsString("https://presigned.test/gathering-descriptions/x.jpg"))
			}
		}

		it("소개 이미지가 아닌 타 프리픽스 key는 404다") {
			get("/images/gatherings/x.jpg") {
				noRedirect()
			} expect {
				status(404)
			}
		}

		it("경로 조작(..)이 섞인 key는 컨트롤러 도달 전 Spring Security 기본 방화벽(StrictHttpFirewall)이 400으로 차단한다") {
			// URL에 리터럴 ".."이 있으면 StrictHttpFirewall이 필터체인 앞단에서 RequestRejectedException(400)으로 막아,
			// 컨트롤러의 GatheringDescriptionImage.isValidKey 검사(404)까지 가지 않는다. (더 이른 시점의 방어라 안전하다)
			get("/images/gathering-descriptions/../secret") {
				noRedirect()
			} expect {
				status(400)
			}
		}
	}
})
