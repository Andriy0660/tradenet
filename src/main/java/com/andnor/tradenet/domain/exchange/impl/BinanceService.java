package com.andnor.tradenet.domain.exchange.impl;

import com.andnor.tradenet.domain.exchange.ExchangeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class BinanceService implements ExchangeService {
    public BigDecimal getCurrentPrice(String symbol) {
        BigDecimal price = BigDecimal.valueOf(100_000);
        log.info("Getting current price for {}: {}", symbol, price);
        return price;
    }

    public void openLongPosition(String symbol) {
        log.info("Opening LONG position: SYMBOL = {}", symbol);
    }

    public void openShortPosition(String symbol) {
        log.info("Opening SHORT position: SYMBOL = {}", symbol);
    }

    public void closePosition(String symbol) {
        log.info("Closing position for {}", symbol);
    }

    public BigDecimal getAccountBalance() {
        log.info("Getting account balance");
        return BigDecimal.valueOf(100_000);
    }
}
