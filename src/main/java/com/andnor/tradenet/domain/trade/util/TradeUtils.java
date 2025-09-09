package com.andnor.tradenet.domain.trade.util;

import com.andnor.tradenet.domain.tradingpair.persistence.TradingPairEntity;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;

@UtilityClass
public class TradeUtils {
    public BigDecimal calculateStep(TradingPairEntity pair) {
        return pair.getStartPrice()
                .multiply(pair.getGridLevelPercentage())
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
    }
}
