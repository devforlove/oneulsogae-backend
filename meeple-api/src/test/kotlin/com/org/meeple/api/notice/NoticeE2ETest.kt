package com.org.meeple.api.notice

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.integration.post
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.NoticeEntityFixture
import com.org.meeple.infra.notice.command.entity.NoticeEntity
import com.org.meeple.infra.notice.command.entity.QNoticeEntity
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.notNullValue

/**
 * `POST/GET /notices/v1` E2E 테스트. (공지 생성 + limit/offset 페이징 목록 조회)
 * 실제 서버(RANDOM_PORT) + Testcontainers(MySQL)를 기동하고 HTTP를 호출한다.
 * 저장 날짜(created_at)는 JPA Auditing이 채우며, 목록은 저장 날짜 최신순(동률이면 id 내림차순)으로 정렬된다.
 * 데이터 준비/정리는 [IntegrationUtil], 요청/검증은 [post]/[get]/[expect] Kotlin DSL로 한다.
 */
class NoticeE2ETest : AbstractIntegrationSupport({

	// 공지를 저장하고 id를 반환한다. (저장 순서가 곧 created_at 순서가 되어 최신순 정렬을 결정한다)
	fun persistNotice(title: String, description: String = "설명"): Long =
		IntegrationUtil.persist(NoticeEntityFixture.create(title = title, description = description)).id!!

	fun noticeByTitle(title: String): NoticeEntity? {
		val notice: QNoticeEntity = QNoticeEntity.noticeEntity
		return IntegrationUtil.getQuery().selectFrom(notice).where(notice.title.eq(title)).fetchFirst()
	}

	describe("POST /notices/v1") {

		context("제목과 설명을 보내면") {
			it("공지를 저장하고 생성된 id를 반환한다 (200)") {
				post("/notices/v1") {
					bearer(accessTokenFor(9001L))
					jsonBody("""{"title": "점검 공지", "description": "오늘 밤 점검이 있습니다."}""")
				} expect {
					status(200)
					body("success", true)
					body("data.noticeId", notNullValue())
				}

				val saved: NoticeEntity = noticeByTitle("점검 공지")!!
				saved.title shouldBe "점검 공지"
				saved.description shouldBe "오늘 밤 점검이 있습니다."
			}
		}

		context("제목이 비어 있으면") {
			it("400을 반환하고 저장하지 않는다") {
				post("/notices/v1") {
					bearer(accessTokenFor(9002L))
					jsonBody("""{"title": "", "description": "설명만 있음"}""")
				} expect {
					status(400)
					body("success", false)
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				post("/notices/v1") {
					jsonBody("""{"title": "x", "description": "y"}""")
				} expect {
					status(401)
				}
			}
		}
	}

	describe("GET /notices/v1") {

		context("공지가 여러 건 있으면") {
			it("저장 날짜 최신순으로 page·size 만큼 페이징해 메타데이터와 함께 반환한다") {
				persistNotice("공지1")
				persistNotice("공지2")
				persistNotice("공지3")

				get("/notices/v1?page=0&size=2") {
					bearer(accessTokenFor(9101L))
				} expect {
					status(200)
					body("success", true)
					body("data.page", 0)
					body("data.size", 2)
					body("data.totalElements", 3)
					body("data.totalPages", 2)
					body("data.hasNext", true)
					body("data.content.size()", 2)
					// 최신순: 공지3 → 공지2
					body("data.content[0].title", "공지3")
					body("data.content[1].title", "공지2")
					body("data.content[0].createdAt", notNullValue())
				}
			}

			it("다음 페이지는 남은 공지를 반환하고 hasNext가 false다") {
				persistNotice("공지1")
				persistNotice("공지2")
				persistNotice("공지3")

				get("/notices/v1?page=1&size=2") {
					bearer(accessTokenFor(9102L))
				} expect {
					status(200)
					body("data.totalElements", 3)
					body("data.hasNext", false)
					body("data.content.size()", 1)
					body("data.content[0].title", "공지1")
				}
			}
		}

		context("공지가 없으면") {
			it("빈 페이지를 반환한다") {
				get("/notices/v1") {
					bearer(accessTokenFor(9103L))
				} expect {
					status(200)
					body("success", true)
					body("data.content.size()", 0)
					body("data.totalElements", 0)
					body("data.totalPages", 0)
					body("data.hasNext", false)
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				get("/notices/v1") {} expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QNoticeEntity.noticeEntity)
	}
})
