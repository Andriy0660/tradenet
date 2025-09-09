package com.andnor.tradenet.domain.exchange;

import com.andnor.tradenet.domain.position.model.PositionType;
import com.andnor.tradenet.domain.position.persistence.PositionEntity;
import com.andnor.tradenet.domain.tradingpair.persistence.TradingPairEntity;

import java.math.BigDecimal;

public interface ExchangeService {
    boolean isHedgeModeEnabled();

    BigDecimal getCurrentPrice(TradingPairEntity tradingPair);

    PositionEntity openPosition(TradingPairEntity tradingPair, PositionType type,
                                BigDecimal entryPrice, BigDecimal takeProfitPrice);

    void closePosition(PositionEntity positionEntity);

    BigDecimal getAccountBalance();
}
