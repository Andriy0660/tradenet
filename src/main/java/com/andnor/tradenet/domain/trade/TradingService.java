package com.andnor.tradenet.domain.trade;

import com.andnor.tradenet.domain.exchange.impl.BinanceService;
import com.andnor.tradenet.domain.position.model.PositionStatus;
import com.andnor.tradenet.domain.position.model.PositionType;
import com.andnor.tradenet.domain.position.persistence.PositionEntity;
import com.andnor.tradenet.domain.position.persistence.PositionRepository;
import com.andnor.tradenet.domain.telegram.model.MessageType;
import com.andnor.tradenet.domain.telegram.service.MessageService;
import com.andnor.tradenet.domain.trade.model.AlgorithmAction;
import com.andnor.tradenet.domain.trade.model.LevelClosingResult;
import com.andnor.tradenet.domain.trade.util.TradeUtils;
import com.andnor.tradenet.domain.tradingpair.persistence.TradingPairEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingService {
  private final PositionRepository positionRepository;
  private final BinanceService binanceService;
  private final MessageService messageService;

  public void processLevelCrossing(TradingPairEntity pair, BigDecimal currentPrice, BigDecimal newLevelPrice, BigDecimal prevLevelPrice) {
    if (prevLevelPrice == null && pair.getStartPrice().equals(newLevelPrice)) {
      return;
    } else if (prevLevelPrice == null) {
      prevLevelPrice = pair.getStartPrice();
    }

    log.info("Processing level crossing for {}: {} -> Level {}", pair.getSymbol(), currentPrice, newLevelPrice);

    closePositionsWhereStopLossOrderExecuted(pair);

    LevelClosingResult levelClosingResult = closeTakeProfitPositions(pair, newLevelPrice);

    boolean isUpward = newLevelPrice.compareTo(prevLevelPrice) > 0;

    AlgorithmAction action = determineAction(pair, newLevelPrice, isUpward, levelClosingResult);
    executeAction(pair, newLevelPrice, isUpward, action);
  }

  private void closePositionsWhereStopLossOrderExecuted(TradingPairEntity pair) {
    List<PositionEntity> openPositions = positionRepository.getAllByStatusAndTradingPair(PositionStatus.OPEN, pair);
    List<Long> openOrderIds = binanceService.getOpenOrderIdsByTradingPair(pair);

    for (PositionEntity position : openPositions) {
      if (position.getStopLossOrderId() != null && !openOrderIds.contains(position.getStopLossOrderId())) {
          log.info("Stop loss order {} was executed for position {}, closing position",
                  position.getStopLossOrderId(), position.getId());
          position.setStatus(PositionStatus.CLOSED);
          position.setClosedAt(Instant.now());
          positionRepository.save(position);
      }
    }
  }

  private LevelClosingResult closeTakeProfitPositions(TradingPairEntity pair, BigDecimal level) {
    List<PositionEntity> positionsToClose = positionRepository.findPositionsToClose(pair.getId(), level);

    LevelClosingResult result = new LevelClosingResult();
    result.setLevel(level);

    for (PositionEntity position : positionsToClose) {
      try {
        binanceService.closePosition(position);

        position.setStatus(PositionStatus.CLOSED);
        position.setEndPrice(level);
        position.setClosedAt(Instant.now());
        positionRepository.save(position);

        if (position.getType() == PositionType.LONG) {
          result.incrementClosedLongPositions();
        } else {
          result.incrementClosedShortPositions();
        }

        messageService.broadcastPositionMessage(MessageType.SUCCESSFULLY_CLOSED_POSITION, position);

      } catch (Exception e) {
        log.error("Failed to close position {} for pair {} at level {}: {}", position.getId(), pair.getSymbol(), level, e.getMessage(), e);

        position.setStatus(PositionStatus.ERROR);
        positionRepository.save(position);

        result.incrementFailedPositions();
      }
    }

    log.info("Completed closing positions for {} at level {}: {} long, {} short closed, {} failed", pair.getSymbol(), level,
            result.getClosedLongPositions(), result.getClosedShortPositions(), result.getFailedPositions());

    return result;
  }

  private AlgorithmAction determineAction(TradingPairEntity pair, BigDecimal newLevelPrice, boolean isUpward, LevelClosingResult closingResult) {
    if (binanceService.getAccountBalance().compareTo(pair.getPositionAmountUsdt()) < 0) {
      log.info("Not enough balance to open position for {} at level {}", pair.getSymbol(), newLevelPrice);
      return AlgorithmAction.DO_NOTHING;
    }

    boolean hasAnyOpenPositions = positionRepository.existsByTradingPairId(pair.getId());
    boolean hasOpenTrendPosition = hasOpenTrendPosition(pair, newLevelPrice, isUpward);
    int totalClosedPositions = closingResult.getClosedLongPositions() + closingResult.getClosedShortPositions();
    boolean hadTakeProfitClosing = totalClosedPositions > 0;
    boolean hadTrendPositionClosed = hasTrendPositionBeenClosed(closingResult, isUpward);

    if (!hasAnyOpenPositions || (hadTrendPositionClosed && !hasOpenTrendPosition)) {
      return AlgorithmAction.OPEN_TREND_POSITION;
    } else if (!hadTakeProfitClosing && hasOpenTrendPosition) {
      return AlgorithmAction.OPEN_COUNTER_TREND_POSITION;
    } else {
      return AlgorithmAction.DO_NOTHING;
    }
  }

  private boolean hasTrendPositionBeenClosed(LevelClosingResult closingResult, boolean isUpward) {
    if (isUpward) {
      return closingResult.getClosedLongPositions() > 0;
    } else {
      return closingResult.getClosedShortPositions() > 0;
    }
  }

  private void executeAction(TradingPairEntity pair, BigDecimal newLevelPrice, boolean isUpward, AlgorithmAction action) {
    switch (action) {
    case OPEN_TREND_POSITION:
      log.info("Opening trend position for {} at level {}", pair.getSymbol(), newLevelPrice);
      openTrendPosition(pair, newLevelPrice, isUpward);
      break;
    case OPEN_COUNTER_TREND_POSITION:
      log.info("Opening counter-trend position for {} at level {}", pair.getSymbol(), newLevelPrice);
      openCounterTrendPosition(pair, newLevelPrice, isUpward);
      break;
    case DO_NOTHING:
      log.info("No action needed for {} at level {}", pair.getSymbol(), newLevelPrice);
      break;
    }
  }

  private void openTrendPosition(TradingPairEntity pair, BigDecimal newLevelPrice, boolean isUpward) {
    PositionType positionType = isUpward ? PositionType.LONG : PositionType.SHORT;
    BigDecimal takeProfitLevelPrice = calculateNextLevel(pair, newLevelPrice, isUpward);

    try {
      PositionEntity position = binanceService.openPosition(pair, positionType, newLevelPrice, takeProfitLevelPrice);
      positionRepository.save(position);

      messageService.broadcastPositionMessage(MessageType.SUCCESSFULLY_OPENED_POSITION, position);

    } catch (Exception e) {
      log.error("Failed to open {} position for {} at level {}: {}", positionType, pair.getSymbol(), newLevelPrice, e.getMessage(), e);
      throw new RuntimeException("Failed to open trend position", e);
    }
  }

  private void openCounterTrendPosition(TradingPairEntity pair, BigDecimal newLevelPrice, boolean isUpward) {
    PositionType positionType = isUpward ? PositionType.SHORT : PositionType.LONG;
    BigDecimal takeProfitLevelPrice = calculatePreviousLevel(pair, newLevelPrice, isUpward);

    try {
      PositionEntity position = binanceService.openPosition(pair, positionType, newLevelPrice, takeProfitLevelPrice);
      positionRepository.save(position);

      messageService.broadcastPositionMessage(MessageType.SUCCESSFULLY_OPENED_POSITION, position);
    } catch (Exception e) {
      log.error("Failed to open counter-trend {} position for {} at level {}: {}", positionType, pair.getSymbol(), newLevelPrice, e.getMessage(), e);
      throw new RuntimeException("Failed to open counter-trend position", e);
    }
  }

  private boolean hasOpenTrendPosition(TradingPairEntity pair, BigDecimal newLevelPrice, boolean isUpward) {
    PositionType trendType = isUpward ? PositionType.LONG : PositionType.SHORT;
    return positionRepository.existsOpenPositionAtLevel(pair.getId(), newLevelPrice, trendType);
  }

  private BigDecimal calculateNextLevel(TradingPairEntity pair, BigDecimal currentLevelPrice, boolean isUpward) {
    BigDecimal step = TradeUtils.calculateStep(pair);
    return isUpward ? currentLevelPrice.add(step) : currentLevelPrice.subtract(step);
  }

  private BigDecimal calculatePreviousLevel(TradingPairEntity pair, BigDecimal currentLevelPrice, boolean isUpward) {
    BigDecimal step = TradeUtils.calculateStep(pair);
    return isUpward ? currentLevelPrice.subtract(step) : currentLevelPrice.add(step);
  }
}
