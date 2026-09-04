package com.portfolio.chungyak.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@code float[]} ↔ JSON 배열 문자열. 임베딩 벡터를 DB 에 담기 위한 것.
 *
 * 벡터 검색이 앱 메모리 코사인이라 DB 는 그냥 문자열로 들고 있으면 된다
 * (공고 수백 건 규모 — 네이티브 벡터 타입/인덱스는 과잉).
 */
@Converter
public class FloatArrayJsonConverter implements AttributeConverter<float[], String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        try {
            return MAPPER.writeValueAsString(attribute == null ? new float[0] : attribute);
        } catch (Exception e) {
            throw new IllegalStateException("임베딩 직렬화 실패", e);
        }
    }

    @Override
    public float[] convertToEntityAttribute(String dbData) {
        try {
            return dbData == null || dbData.isBlank() ? new float[0] : MAPPER.readValue(dbData, float[].class);
        } catch (Exception e) {
            throw new IllegalStateException("임베딩 역직렬화 실패", e);
        }
    }
}
