package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.exception.BadRequestException;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

final class InspectionChecklistSearch {
    private static final String LOWER_CASE_ACCENTS = "àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ";
    // Some PostgreSQL C locales do not lowercase Vietnamese letters, including Đ.
    static final String ACCENTED_CHARACTERS = LOWER_CASE_ACCENTS + LOWER_CASE_ACCENTS.toUpperCase(Locale.ROOT);
    static final String REPLACEMENT_CHARACTERS = "aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd".repeat(2);

    private InspectionChecklistSearch() {
    }

    static String normalizedPattern(String keyword) {
        if (keyword == null) {
            return null;
        }
        if (keyword.length() > 120) {
            throw new BadRequestException("Từ khóa tìm kiếm tối đa 120 ký tự");
        }
        String normalized = Normalizer.normalize(keyword, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replaceAll("(?U)\\s+", " ")
                .strip();
        if (normalized.isEmpty()) {
            return null;
        }
        String pattern = Arrays.stream(normalized.split(" "))
                .map(InspectionChecklistSearch::escapeLiteral)
                .collect(Collectors.joining("%"));
        return "%" + pattern + "%";
    }

    private static String escapeLiteral(String word) {
        return word.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
