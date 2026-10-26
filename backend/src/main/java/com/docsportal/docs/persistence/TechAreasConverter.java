package com.docsportal.docs.persistence;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * tech_areas 를 "BE,FE" 형태의 한 컬럼으로 저장한다. 1단계에서 만든 행은 null 이므로 빈 집합으로 읽는다.
 */
@Converter
public class TechAreasConverter implements AttributeConverter<Set<TechArea>, String> {

    @Override
    public String convertToDatabaseColumn(Set<TechArea> areas) {
        return areas == null || areas.isEmpty() ? null
            : areas.stream().sorted().map(TechArea::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<TechArea> convertToEntityAttribute(String column) {
        Set<TechArea> areas = EnumSet.noneOf(TechArea.class);
        if (column != null && !column.isBlank()) {
            Arrays.stream(column.split(",")).map(TechArea::valueOf).forEach(areas::add);
        }
        return areas;
    }
}
