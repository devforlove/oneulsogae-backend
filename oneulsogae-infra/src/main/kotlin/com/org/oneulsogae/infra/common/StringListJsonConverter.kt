package com.org.oneulsogae.infra.common

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

/**
 * [List]<[String]>을 JSON 문자열로 직렬화해 단일 컬럼에 보관하는 JPA 컨버터.
 * 취미/관심사처럼 개수가 가변적인 문자열 목록을 별도 테이블 없이 JSON 컬럼에 저장한다.
 * null/빈 문자열은 빈 리스트로 복원한다.
 */
@Converter
class StringListJsonConverter : AttributeConverter<List<String>, String> {

	override fun convertToDatabaseColumn(attribute: List<String>?): String =
		MAPPER.writeValueAsString(attribute ?: emptyList<String>())

	override fun convertToEntityAttribute(dbData: String?): List<String> =
		if (dbData.isNullOrBlank()) emptyList() else MAPPER.readValue(dbData)

	companion object {
		private val MAPPER = jacksonObjectMapper()
	}
}
