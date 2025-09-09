package com.andnor.tradenet.domain.trade.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class LevelClosingResult {
    private BigDecimal level;
    private int closedLongPositions = 0;
    private int closedShortPositions = 0;
    private int failedPositions = 0;

    public void incrementClosedLongPositions() {
        this.closedLongPositions++;
    }

    public void incrementClosedShortPositions() {
        this.closedShortPositions++;
    }

    public void incrementFailedPositions() {
        this.failedPositions++;
    }
}
