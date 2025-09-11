package com.andnor.tradenet.domain.exchange.impl;

import com.andnor.tradenet.core.model.SymbolInfo;
import com.andnor.tradenet.domain.exchange.ExchangeService;
import com.andnor.tradenet.domain.position.model.PositionStatus;
import com.andnor.tradenet.domain.position.model.PositionType;
import com.andnor.tradenet.domain.position.persistence.PositionEntity;
import com.andnor.tradenet.domain.tradingpair.persistence.TradingPairEntity;
import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class BinanceService implements ExchangeService {
    private final ObjectMapper mapper;
    private final UMFuturesClientImpl client;
    private final Map<String, SymbolInfo> symbolInfoCache;

    @Override
    public boolean isHedgeModeEnabled() {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            String result = client.account().getCurrentPositionMode(params);
            JsonNode accountInfo = mapper.readTree(result);

            if (accountInfo.has("dualSidePosition")) {
                return accountInfo.get("dualSidePosition").asBoolean();
            }

            return false;
        } catch (Exception e) {
            log.error("Failed to check account hedge mode: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to verify hedge mode setting", e);
        }
    }

    @Override
    public BigDecimal getCurrentPrice(TradingPairEntity tradingPair) {
        String symbol = tradingPair.getSymbol();
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("symbol", symbol);

        String result = client.market().tickerSymbol(params);
        try {
            JsonNode node = mapper.readTree(result);
            BigDecimal price = new BigDecimal(node.get("price").asText());
            return price;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse price response: " + result, e);
        }
    }

    @Override
    public PositionEntity openPosition(TradingPairEntity tradingPair, PositionType type,
                                       BigDecimal entryPrice, BigDecimal takeProfitPrice) {
        String symbol = tradingPair.getSymbol();
        BigDecimal usdAmount = tradingPair.getPositionAmountUsdt();
        String positionSide = type.toString();

        BigDecimal stopLossPercent = (type == PositionType.LONG)
                                     ? tradingPair.getLongStopLossPercentage()
                                     : tradingPair.getShortStopLossPercentage();

        try {
            BigDecimal currentPrice = getCurrentPrice(symbol);

            SymbolInfo info = symbolInfoCache.get(symbol);
            if (info == null) {
                throw new IllegalStateException("No symbol info found for: " + symbol);
            }

            BigDecimal quantity = usdAmount
                    .divide(currentPrice, 8, RoundingMode.HALF_UP)
                    .setScale(info.getQuantityPrecision(), RoundingMode.DOWN);

            if (quantity.equals(BigDecimal.ZERO)) {
                throw new IllegalStateException("Quantity is zero. You should edit trading pair settings");
            }

            BigDecimal stopLossPrice = calculateStopLoss(entryPrice, type, stopLossPercent)
                    .setScale(info.getPricePrecision(), RoundingMode.DOWN);

            LinkedHashMap<String, Object> params = getParamsForMarketOrder(type, symbol, quantity, positionSide);

            String orderResult = client.account().newOrder(params);
            JSONObject orderJson = new JSONObject(orderResult);
            long orderId = orderJson.getLong("orderId");

            JSONObject finalOrder = waitForOrderFill(symbol, orderId);

            String executedQty = finalOrder.getString("executedQty");
            String avgPrice = finalOrder.getString("avgPrice");

            log.info("Opened {} {} for {} USDT: qty={}, avgPrice={}",
                     type, symbol, usdAmount, executedQty, avgPrice);

            LinkedHashMap<String, Object> slParams = getParamsForStopMarketOrder(type, symbol, stopLossPrice, positionSide);

            String slResult = client.account().newOrder(slParams);
            log.info("Placed STOP LOSS at {} for {} {}", stopLossPrice, type, symbol);

            return PositionEntity.builder()
                    .tradingPair(tradingPair)
                    .gridLevelPrice(entryPrice)
                    .quantity(new BigDecimal(executedQty))
                    .type(type)
                    .status(PositionStatus.OPEN)
                    .startPrice(new BigDecimal(avgPrice))
                    .stopLossPrice(stopLossPrice)
                    .takeProfitPrice(takeProfitPrice)
                    .openedAt(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to open {} position for {}: {}", type, symbol, e.getMessage(), e);
            throw new RuntimeException("Failed to open position for " + symbol, e);
        }
    }

    private JSONObject waitForOrderFill(String symbol, long orderId) throws InterruptedException {
        LinkedHashMap<String, Object> query = new LinkedHashMap<>();
        query.put("symbol", symbol);
        query.put("orderId", orderId);

        for (int i = 0; i < 20; i++) {
            String orderInfo = client.account().queryOrder(query);
            JSONObject orderJson = new JSONObject(orderInfo);
            String status = orderJson.getString("status");

            if ("FILLED".equals(status)) {
                return orderJson;
            }
            if (!"NEW".equals(status) && !"PARTIALLY_FILLED".equals(status)) {
                throw new IllegalStateException("Order " + orderId + " ended with status " + status);
            }

            Thread.sleep(200);
        }

        throw new IllegalStateException("Order " + orderId + " not filled in time");
    }

    private LinkedHashMap<String, Object> getParamsForMarketOrder(PositionType type, String symbol, BigDecimal quantity, String positionSide) {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("symbol", symbol);
        params.put("side", (type == PositionType.LONG) ? "BUY" : "SELL");
        params.put("type", "MARKET");
        params.put("quantity", quantity.toPlainString());
        params.put("positionSide", positionSide);
        return params;
    }

    private LinkedHashMap<String, Object> getParamsForStopMarketOrder(PositionType type, String symbol, BigDecimal stopLossPrice, String positionSide) {
        LinkedHashMap<String, Object> slParams = new LinkedHashMap<>();
        slParams.put("symbol", symbol);
        slParams.put("side", (type == PositionType.LONG) ? "SELL" : "BUY");
        slParams.put("type", "STOP_MARKET");
        slParams.put("stopPrice", stopLossPrice.toPlainString());
        slParams.put("closePosition", "true");
        slParams.put("positionSide", positionSide);
        slParams.put("workingType", "MARK_PRICE");
        return slParams;
    }

    private BigDecimal calculateStopLoss(BigDecimal entryPrice, PositionType type, BigDecimal stopLossPercent) {
        BigDecimal multiplier = stopLossPercent.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

        if (type == PositionType.LONG) {
            return entryPrice.multiply(BigDecimal.ONE.subtract(multiplier));
        } else {
            return entryPrice.multiply(BigDecimal.ONE.add(multiplier));
        }
    }

    private BigDecimal getCurrentPrice(String symbol) {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            String result = client.market().tickerSymbol(params);
            JSONObject json = new JSONObject(result);
            return new BigDecimal(json.getString("price"));
        } catch (Exception e) {
            log.error("Failed to get current price for {}: {}", symbol, e.getMessage());
            throw new RuntimeException("Failed to get current price for " + symbol, e);
        }
    }

    @Override
    public void closePosition(PositionEntity positionEntity) {
        String symbol = positionEntity.getTradingPair().getSymbol();
        String positionSide = positionEntity.getType().toString();

        log.info("Closing {} position for {}", positionSide, symbol);

        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            String result = client.account().positionInformation(params);
            JsonNode positions = mapper.readTree(result);

            if (positions.isArray()) {
                for (JsonNode pos : positions) {
                    String apiPositionSide = pos.get("positionSide").asText();

                    if (positionSide.equals(apiPositionSide)) {
                        BigDecimal positionAmt = new BigDecimal(pos.get("positionAmt").asText());

                        if (positionAmt.compareTo(BigDecimal.ZERO) != 0) {
                            String orderSide = positionSide.equals("LONG") ? "SELL" : "BUY";
                            BigDecimal qtyToClose = positionAmt.abs();

                            boolean isPartialClose = qtyToClose.compareTo(positionAmt.abs()) < 0;

                            LinkedHashMap<String, Object> closeParams = new LinkedHashMap<>();
                            closeParams.put("symbol", symbol);
                            closeParams.put("side", orderSide);
                            closeParams.put("type", "MARKET");
                            closeParams.put("quantity", qtyToClose.toPlainString());
                            closeParams.put("positionSide", positionSide);

                            if (isPartialClose) {
                                closeParams.put("reduceOnly", true);
                            }
                            String closeResult = client.account().newOrder(closeParams);
                            log.info("Closed {} {} contracts on {} {}: {}",
                                     qtyToClose, orderSide, symbol, positionSide, closeResult);
                            return;
                        } else {
                            log.info("No open {} position found for {}", positionSide, symbol);
                            positionEntity.setStatus(PositionStatus.CLOSED);
                            positionEntity.setClosedAt(Instant.now());
                            return;
                        }
                    }
                }

                log.warn("Position side {} not found in API response for {}", positionSide, symbol);
            }

        } catch (Exception e) {
            log.error("Failed to close {} position for {}", positionSide, symbol, e);
            throw new RuntimeException("Failed to close position for " + symbol, e);
        }
    }

    @Override
    public BigDecimal getAccountBalance() {
        String result = client.account().futuresAccountBalance(new LinkedHashMap<>());
        try {
            JsonNode node = mapper.readTree(result);
            for (JsonNode balance : node) {
                if ("USDT".equals(balance.get("asset").asText())) {
                    BigDecimal free = new BigDecimal(balance.get("balance").asText());
                    log.info("Getting account balance: {}", free);
                    return free;
                }
            }
            return BigDecimal.ZERO;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse balance response: " + result, e);
        }
    }
}
