package com.bluemoon.backend.domain.insight;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyPick {

    private Long id;
    private Long accountId;
    private Long insightId;
    private LocalDate pickDate;

    public DailyPick(Long accountId, Long insightId, LocalDate pickDate) {
        this.accountId = accountId;
        this.insightId = insightId;
        this.pickDate = pickDate;
    }
}
