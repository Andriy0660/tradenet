package com.andnor.tradenet.domain.trade.thread;

import com.andnor.tradenet.domain.exchange.impl.BinanceService;
import com.andnor.tradenet.domain.trade.TradingService;
import com.andnor.tradenet.domain.trade.utils.GridLevelCalculator;
import com.andnor.tradenet.domain.tradingpair.persistence.TradingPairEntity;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
public class TradingThread implements Runnable {

    private final TradingPairEntity tradingPair;
    private final BinanceService binanceService;
    private final TradingService tradingService;
    private volatile boolean running = true;
    private BigDecimal lastPrice;
    private final List<BigDecimal> levels;
    private BigDecimal currentLevel;
    private long TIME_INTERVAL = 500;

    public TradingThread(TradingPairEntity tradingPair, BinanceService binanceService, TradingService tradingService) {
        this.tradingPair = tradingPair;
        this.binanceService = binanceService;
        this.tradingService = tradingService;
        this.levels = GridLevelCalculator.calculateLevels(tradingPair);
    }

    @Override
    public void run() {
        log.info("Starting trading thread for {}", tradingPair.getSymbol());

        while (running) {
            try {
                BigDecimal currentPrice = binanceService.getCurrentPrice(tradingPair.getSymbol());

                if (lastPrice != null) {
                    processLevelCrossings(lastPrice, currentPrice);
                }

                lastPrice = currentPrice;
                Thread.sleep(TIME_INTERVAL);

            } catch (InterruptedException e) {
                log.info("Trading thread for {} interrupted", tradingPair.getSymbol());
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in trading thread for {}: {}", tradingPair.getSymbol(), e.getMessage());
            }
        }

        log.info("Trading thread stopped for {}", tradingPair.getSymbol());
    }

    private void processLevelCrossings(BigDecimal oldPrice, BigDecimal newPrice) {
        for (BigDecimal level : levels) {
            if (hasCrossedLevel(oldPrice, newPrice, level)) {
                if (currentLevel == null || !currentLevel.equals(level)) {
                    log.info("Level crossing detected for {}: {} -> {} (Level: {})",
                             tradingPair.getSymbol(), oldPrice, newPrice, level);

                    tradingService.processLevelCrossing(tradingPair, newPrice, level, currentLevel);
                    currentLevel = level;
                    break;
                }
            }
        }
    }

    private boolean hasCrossedLevel(BigDecimal oldPrice, BigDecimal newPrice, BigDecimal level) {
        // TODO discuss it
        BigDecimal deltaPercent = BigDecimal.valueOf(0.1);
        BigDecimal delta = level.multiply(deltaPercent).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

        boolean crossedUp = oldPrice.add(delta).compareTo(level) < 0 && newPrice.compareTo(level) >= 0;
        boolean crossedDown = oldPrice.subtract(delta).compareTo(level) > 0 && newPrice.compareTo(level) <= 0;

        return crossedUp || crossedDown;
    }


    public void stop() {
        running = false;
    }
}
