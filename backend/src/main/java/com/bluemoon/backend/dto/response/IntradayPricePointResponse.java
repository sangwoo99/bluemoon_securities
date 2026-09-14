package com.bluemoon.backend.dto.response;

import java.math.BigDecimal;

/** time: "HHMMSS" 형식의 체결 시각 문자열 (KIS 응답 그대로 전달, 프론트에서 표시용으로 가공). */
public record IntradayPricePointResponse(String time, BigDecimal price) {
}
