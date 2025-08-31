package com.andnor.tradenet.domain.trade.utils;

import com.andnor.tradenet.domain.tradingpair.persistence.TradingPairEntity;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@UtilityClass
public class GridLevelCalculator {
    // TODO discuss it
    public List<BigDecimal> calculateLevels(TradingPairEntity pair) {
        List<BigDecimal> levels = new ArrayList<>();
        BigDecimal currentLevel = pair.getMinPrice();
        BigDecimal step = currentLevel.multiply(pair.getLevelPercentage()).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

        while (currentLevel.compareTo(pair.getMaxPrice()) <= 0) {
            levels.add(currentLevel);
            currentLevel = currentLevel.add(step);
            step = currentLevel.multiply(pair.getLevelPercentage()).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        }
        return levels;
    }
}
