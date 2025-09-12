package com.andnor.tradenet.domain.exchange;

import com.andnor.tradenet.domain.position.model.PositionType;
import com.andnor.tradenet.domain.position.persistence.PositionEntity;
import com.andnor.tradenet.domain.tradingpair.persistence.TradingPairEntity;

import java.math.BigDecimal;
import java.util.List;

public interface ExchangeService {
    boolean isHedgeModeEnabled();

    BigDecimal getCurrentPrice(TradingPairEntity tradingPair);

    PositionEntity openPosition(TradingPairEntity tradingPair, PositionType type,
                                BigDecimal entryPrice, BigDecimal takeProfitPrice);

    void closePosition(PositionEntity positionEntity);

    BigDecimal getAccountBalance();

    List<Long> getOpenOrderIdsByTradingPair(TradingPairEntity tradingPair);
}
