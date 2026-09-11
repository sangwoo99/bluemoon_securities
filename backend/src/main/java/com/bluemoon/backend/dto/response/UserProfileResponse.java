package com.bluemoon.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserProfileResponse(
        String email,
        String name,
        LocalDateTime createdAt,
        BigDecimal cashBalance,
        LocalDateTime accountCreatedAt
) {
}
