package com.org.oneulsogae.api.offline

import com.org.oneulsogae.admin.gathering.query.service.port.`in`.GetGatheringDescriptionImageUrlUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * 소개 이미지 공개 프록시. key로 새 presigned GET URL을 발급해 302 리다이렉트한다(비공개 버킷 유지, 만료 무관).
 * gathering-descriptions/ 프리픽스 key만 허용 — 그 외는 404. 비로그인 소비자도 상세에서 이미지를 봐야 하므로 공개(permitAll).
 */
@Tag(name = "소개 이미지 프록시", description = "모임 소개(Markdown)에 삽입된 이미지를 presigned URL로 302 리다이렉트하는 공개 엔드포인트.")
@RestController
class GatheringDescriptionImageController(
	private val useCase: GetGatheringDescriptionImageUrlUseCase,
) {

	@Operation(
		summary = "소개 이미지 조회(302 리다이렉트)",
		description = "gathering-descriptions/ 프리픽스 key만 허용한다. 유효하면 presigned GET URL로 302 리다이렉트, " +
			"아니면(타 프리픽스·경로 조작 등) 404. 인증 불필요.",
	)
	// {*key}는 슬래시 포함 나머지 경로를 앞 슬래시와 함께 캡처한다(예: "/gathering-descriptions/uuid.jpg").
	@GetMapping("/images/{*key}")
	fun image(@PathVariable key: String): ResponseEntity<Void> {
		val normalized = key.removePrefix("/")
		val url = useCase.execute(normalized) ?: return ResponseEntity.notFound().build()
		return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build()
	}
}
