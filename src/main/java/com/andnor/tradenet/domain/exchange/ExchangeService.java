package com.andnor.tradenet.domain.exchange;

import com.andnor.tradenet.domain.tradingpair.persistence.TradingPairEntity;

import java.math.BigDecimal;

public interface ExchangeService {
    BigDecimal getCurrentPrice(TradingPairEntity tradingPair);

    void openLongPosition(TradingPairEntity tradingPair);

    void openShortPosition(TradingPairEntity tradingPair);

    void closePosition(TradingPairEntity tradingPair);

    BigDecimal getAccountBalance();
}
