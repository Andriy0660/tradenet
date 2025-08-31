package com.andnor.tradenet.domain.trade;

import com.andnor.tradenet.domain.exchange.impl.BinanceService;
import com.andnor.tradenet.domain.position.model.PositionStatus;
import com.andnor.tradenet.domain.position.model.PositionType;
import com.andnor.tradenet.domain.position.persistence.PositionEntity;
import com.andnor.tradenet.domain.position.persistence.PositionRepository;
import com.andnor.tradenet.domain.trade.model.AlgorithmAction;
import com.andnor.tradenet.domain.trade.model.LevelClosingResult;
import com.andnor.tradenet.domain.tradingpair.persistence.TradingPairEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingService {
    private final PositionRepository positionRepository;
    private final BinanceService binanceService;

    public void processLevelCrossing(TradingPairEntity pair, BigDecimal currentPrice, BigDecimal newLevel, BigDecimal prevLevel) {
        log.info("Processing level crossing for {}: {} -> Level {}",
                 pair.getSymbol(), currentPrice, newLevel);

        LevelClosingResult levelClosingResult = closeTakeProfitPositions(pair, newLevel);

        boolean isUpward = newLevel.compareTo(prevLevel) > 0;

        AlgorithmAction action = determineAction(pair, newLevel, isUpward, levelClosingResult);
        executeAction(pair, newLevel, isUpward, action);
    }

    private LevelClosingResult closeTakeProfitPositions(TradingPairEntity pair, BigDecimal level) {
        List<PositionEntity> positionsToClose = positionRepository
                .findOpenPositionsWithTakeProfitAt(pair.getSymbol(), level);

        LevelClosingResult result = new LevelClosingResult();
        result.setLevel(level);

        for (PositionEntity position : positionsToClose) {
            binanceService.closePosition(pair.getSymbol());
            position.setStatus(PositionStatus.CLOSED);
            position.setEndPrice(level);
            position.setClosedAt(Instant.now());
            positionRepository.save(position);

            if (position.getType() == PositionType.LONG) {
                result.incrementClosedLongPositions();
            } else {
                result.incrementClosedShortPositions();
            }

            log.info("Closed position {} with profit", position.getId());
        }

        return result;
    }

    private AlgorithmAction determineAction(TradingPairEntity pair, BigDecimal level,
                                            boolean isUpward, LevelClosingResult closingResult) {

        boolean hasOpenTrendPosition = hasOpenTrendPosition(pair, level, isUpward);
        int totalClosedPositions = closingResult.getClosedLongPositions() + closingResult.getClosedShortPositions();
        boolean hadTakeProfitClosing = totalClosedPositions > 0;
        boolean hadTrendPositionClosed = hasTrendPositionBeenClosed(closingResult, isUpward);

        if (hadTrendPositionClosed && !hasOpenTrendPosition) {
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

    private void executeAction(TradingPairEntity pair, BigDecimal level, boolean isUpward, AlgorithmAction action) {
        switch (action) {
            case OPEN_TREND_POSITION:
                openTrendPosition(pair, level, isUpward);
                break;
            case OPEN_COUNTER_TREND_POSITION:
                openCounterTrendPosition(pair, level, isUpward);
                break;
            case DO_NOTHING:
                log.info("No action needed for {} at level {}", pair.getSymbol(), level);
                break;
        }
    }

    private void openTrendPosition(TradingPairEntity pair, BigDecimal level, boolean isUpward) {
        PositionType positionType = isUpward ? PositionType.LONG : PositionType.SHORT;
        BigDecimal takeProfitLevel = calculateNextLevel(pair, level, isUpward);

        PositionEntity position = createPosition(pair, level, positionType, takeProfitLevel);
        positionRepository.save(position);

        if (isUpward) {
            binanceService.openLongPosition(pair.getSymbol());
        } else {
            binanceService.openShortPosition(pair.getSymbol());
        }

        positionRepository.save(position);
    }

    private void openCounterTrendPosition(TradingPairEntity pair, BigDecimal level, boolean isUpward) {
        PositionType positionType = isUpward ? PositionType.SHORT : PositionType.LONG;
        BigDecimal takeProfitLevel = calculatePreviousLevel(pair, level, isUpward);

        PositionEntity position = createPosition(pair, level, positionType, takeProfitLevel);
        positionRepository.save(position);

        if (isUpward) {
            binanceService.openShortPosition(pair.getSymbol());
        } else {
            binanceService.openLongPosition(pair.getSymbol());
        }

        positionRepository.save(position);
    }

    private boolean hasOpenTrendPosition(TradingPairEntity pair, BigDecimal level, boolean isUpward) {
        PositionType trendType = isUpward ? PositionType.LONG : PositionType.SHORT;
        return positionRepository.existsOpenPositionAtLevel(pair.getSymbol(), level, trendType);
    }

    private PositionEntity createPosition(TradingPairEntity pair, BigDecimal gridLevel, PositionType type, BigDecimal tpLevel) {
        return PositionEntity.builder()
                .tradingPair(pair)
                .gridLevel(gridLevel)
                .type(type)
                .status(PositionStatus.OPEN)
                .startPrice(gridLevel)
                .stopLossPrice(calculateStopLoss(gridLevel, type, pair.getStopLossPercentage()))
                .openedAt(Instant.now())
                .build();
    }


    private BigDecimal calculateStopLoss(BigDecimal entryPrice, PositionType type, BigDecimal slPercentage) {
        BigDecimal slAmount = entryPrice.multiply(slPercentage).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        return type == PositionType.LONG ?
               entryPrice.subtract(slAmount) :
               entryPrice.add(slAmount);
    }

    private BigDecimal calculateNextLevel(TradingPairEntity pair, BigDecimal currentLevel, boolean isUpward) {
        BigDecimal step = currentLevel.multiply(pair.getLevelPercentage()).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        return isUpward ? currentLevel.add(step) : currentLevel.subtract(step);
    }

    private BigDecimal calculatePreviousLevel(TradingPairEntity pair, BigDecimal currentLevel, boolean isUpward) {
        BigDecimal step = currentLevel.multiply(pair.getLevelPercentage()).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        return isUpward ? currentLevel.subtract(step) : currentLevel.add(step);
    }
}
