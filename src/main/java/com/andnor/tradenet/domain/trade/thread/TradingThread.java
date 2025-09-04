package com.andnor.tradenet.domain.trade.thread;

import com.andnor.tradenet.domain.exchange.impl.BinanceService;
import com.andnor.tradenet.domain.trade.TradingService;
import com.andnor.tradenet.domain.tradingpair.persistence.TradingPairEntity;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
public class TradingThread implements Runnable {
  private volatile boolean running = true;

  private final TradingPairEntity tradingPair;
  private final BinanceService binanceService;
  private final TradingService tradingService;
  private BigDecimal lastPrice;
  private BigDecimal currentLevelPrice;

  private static final long TIME_INTERVAL = 500;

  public TradingThread(TradingPairEntity tradingPair, BinanceService binanceService, TradingService tradingService) {
    this.tradingPair = tradingPair;
    this.binanceService = binanceService;
    this.tradingService = tradingService;
  }

  @Override
  public void run() {
    log.info("Starting trading thread for {}", tradingPair.getSymbol());

    while (running) {
      try {
        BigDecimal currentPrice = binanceService.getCurrentPrice(tradingPair);

        if (lastPrice != null && lastPrice.compareTo(currentPrice) != 0) {
          processLevelCrossings(tradingPair, lastPrice, currentPrice);
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

  private void processLevelCrossings(TradingPairEntity pair, BigDecimal oldPrice, BigDecimal newPrice) {
    BigDecimal step = pair.getStartPrice().multiply(pair.getGridLevelPercentage()).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

    BigDecimal newLevelPrice = findGridLevelPrice(pair.getStartPrice(), step, oldPrice, newPrice);
    if (newLevelPrice != null && (currentLevelPrice == null || !currentLevelPrice.equals(newLevelPrice))) {
      log.info("Level crossing detected for {}: {} -> {} (Level: {})", pair.getSymbol(), oldPrice, newPrice, newLevelPrice);

      tradingService.processLevelCrossing(pair, newPrice, newLevelPrice, currentLevelPrice);
      currentLevelPrice = newLevelPrice;
    }
  }

  public BigDecimal findGridLevelPrice(BigDecimal startPrice, BigDecimal step, BigDecimal oldPrice, BigDecimal newPrice) {
    int cmp = newPrice.compareTo(oldPrice);

    if (cmp == 0) return null;

    BigDecimal diff = newPrice.subtract(startPrice);
    BigDecimal n = diff.divide(step, 0, RoundingMode.DOWN);
    BigDecimal candidate = startPrice.add(n.multiply(step));

    if (cmp > 0) {
      if (candidate.compareTo(oldPrice) <= 0) {
        candidate = candidate.add(step);
      }

      if (candidate.compareTo(oldPrice) > 0 && candidate.compareTo(newPrice) <= 0) {
        return candidate;
      }
    } else {
      while (candidate.compareTo(newPrice) < 0) {
        candidate = candidate.add(step);
      }

      if (candidate.compareTo(oldPrice) < 0) {
        return candidate;
      }
    }

    return null;
  }

  public void stop() {
    running = false;
  }
}
