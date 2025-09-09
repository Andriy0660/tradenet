package com.andnor.tradenet.domain.exchange.impl;

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
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class BinanceService implements ExchangeService {

    private final ObjectMapper mapper;
    private final UMFuturesClientImpl client;

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
            BigDecimal rawQuantity = usdAmount.divide(currentPrice, 8, RoundingMode.HALF_UP);
            BigDecimal quantity = adjustQuantity(symbol, rawQuantity);

            BigDecimal stopLossPrice = calculateStopLoss(entryPrice, type, stopLossPercent);
            stopLossPrice = adjustPrice(symbol, stopLossPrice);

            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            params.put("side", (type == PositionType.LONG) ? "BUY" : "SELL");
            params.put("type", "MARKET");
            params.put("quantity", quantity.toPlainString());
            params.put("positionSide", positionSide);

            String orderResult = client.account().newOrder(params);

            JSONObject json = new JSONObject(orderResult);
            String executedQty = json.getString("executedQty");
            String avgPrice = json.getString("avgPrice");

            log.info("Opened {} {} for {} USDT: qty={}, avgPrice={}",
                     type, symbol, usdAmount, executedQty, avgPrice);

            LinkedHashMap<String, Object> slParams = new LinkedHashMap<>();
            slParams.put("symbol", symbol);
            slParams.put("side", (type == PositionType.LONG) ? "SELL" : "BUY");
            slParams.put("type", "STOP_MARKET");
            slParams.put("stopPrice", stopLossPrice.toPlainString());
            slParams.put("closePosition", "true");
            slParams.put("positionSide", positionSide);
            slParams.put("workingType", "MARK_PRICE");

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

    private BigDecimal adjustQuantity(String symbol, BigDecimal quantity) {
        try {
            String result = client.market().exchangeInfo();
            JSONObject json = new JSONObject(result);
            JSONArray symbols = json.getJSONArray("symbols");

            for (int i = 0; i < symbols.length(); i++) {
                JSONObject s = symbols.getJSONObject(i);
                if (s.getString("symbol").equals(symbol)) {
                    JSONArray filters = s.getJSONArray("filters");
                    for (int j = 0; j < filters.length(); j++) {
                        JSONObject f = filters.getJSONObject(j);
                        if (f.getString("filterType").equals("LOT_SIZE")) {
                            BigDecimal stepSize = new BigDecimal(f.getString("stepSize"));
                            int precision = stepSize.stripTrailingZeros().scale();
                            return quantity.setScale(precision, RoundingMode.DOWN);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to adjust quantity for {}: {}", symbol, e.getMessage());
        }
        return quantity.setScale(3, RoundingMode.DOWN);
    }

    private BigDecimal adjustPrice(String symbol, BigDecimal price) {
        try {
            String result = client.market().exchangeInfo();
            JSONObject json = new JSONObject(result);
            JSONArray symbols = json.getJSONArray("symbols");

            for (int i = 0; i < symbols.length(); i++) {
                JSONObject s = symbols.getJSONObject(i);
                if (s.getString("symbol").equals(symbol)) {
                    JSONArray filters = s.getJSONArray("filters");
                    for (int j = 0; j < filters.length(); j++) {
                        JSONObject f = filters.getJSONObject(j);
                        if (f.getString("filterType").equals("PRICE_FILTER")) {
                            BigDecimal tickSize = new BigDecimal(f.getString("tickSize"));
                            int precision = tickSize.stripTrailingZeros().scale();
                            return price.setScale(precision, RoundingMode.DOWN);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to adjust price for {}: {}", symbol, e.getMessage());
        }
        return price.setScale(2, RoundingMode.DOWN);
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
