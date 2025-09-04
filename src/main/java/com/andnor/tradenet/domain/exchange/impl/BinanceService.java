package com.andnor.tradenet.domain.exchange.impl;

import com.andnor.tradenet.domain.exchange.ExchangeService;
import com.andnor.tradenet.domain.tradingpair.persistence.TradingPairEntity;
import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class BinanceService implements ExchangeService {

    private final ObjectMapper mapper;
    private final UMFuturesClientImpl client;

    @Override
    public BigDecimal getCurrentPrice(TradingPairEntity tradingPair) {
        String symbol = tradingPair.getSymbol();
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("symbol", symbol);

        String result = client.market().tickerSymbol(params);
        try {
            JsonNode node = mapper.readTree(result);
            BigDecimal price = new BigDecimal(node.get("price").asText());
            log.info("Getting current price for {}: {}", symbol, price);
            return price;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse price response: " + result, e);
        }
    }

    @Override
    public void openLongPosition(TradingPairEntity tradingPair) {
        log.info("Opening long position for {}", tradingPair.getSymbol());

        String symbol = tradingPair.getSymbol();
        BigDecimal usdAmount = tradingPair.getPositionAmountUsdt();
        BigDecimal stopLoss = tradingPair.getLongStopLossPercentage()
                .multiply(tradingPair.getStartPrice())
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("symbol", symbol);
        params.put("side", "BUY");
        params.put("type", "MARKET");
        params.put("quoteOrderQty", usdAmount);

        String orderResult = client.account().newOrder(params);
        log.info("Opened LONG {} for {} USDT: {}", symbol, usdAmount, orderResult);

        if (stopLoss != null) {
            LinkedHashMap<String, Object> slParams = new LinkedHashMap<>();
            slParams.put("symbol", symbol);
            slParams.put("side", "SELL");
            slParams.put("type", "STOP_MARKET");
            slParams.put("stopPrice", stopLoss.toPlainString());
            slParams.put("closePosition", "true");
            slParams.put("timeInForce", "GTC");
            String slResult = client.account().newOrder(slParams);
            log.info("Placed STOP LOSS at {} for {}: {}", stopLoss, symbol, slResult);
        }
    }

    @Override
    public void openShortPosition(TradingPairEntity tradingPair) {
        log.info("Opening short position for {}", tradingPair.getSymbol());

        String symbol = tradingPair.getSymbol();
        BigDecimal usdAmount = tradingPair.getPositionAmountUsdt();
        BigDecimal stopLoss = tradingPair.getLongStopLossPercentage()
                .multiply(tradingPair.getStartPrice())
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("symbol", symbol);
        params.put("side", "SELL");
        params.put("type", "MARKET");
        params.put("quoteOrderQty", usdAmount);

        String orderResult = client.account().newOrder(params);
        log.info("Opened SHORT {} for {} USDT: {}", symbol, usdAmount, orderResult);

        if (stopLoss != null) {
            LinkedHashMap<String, Object> slParams = new LinkedHashMap<>();
            slParams.put("symbol", symbol);
            slParams.put("side", "BUY");
            slParams.put("type", "STOP_MARKET");
            slParams.put("stopPrice", stopLoss.toPlainString());
            slParams.put("closePosition", "true");
            slParams.put("timeInForce", "GTC");
            String slResult = client.account().newOrder(slParams);
            log.info("Placed STOP LOSS at {} for {}: {}", stopLoss, symbol, slResult);
        }
    }

    @Override
    public void closePosition(TradingPairEntity tradingPair) {
        String symbol = tradingPair.getSymbol();
        log.info("Closing position for {}", symbol);

        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);

            String result = client.account().positionInformation(params);
            JsonNode positions = mapper.readTree(result);

            for (JsonNode pos : positions) {
                BigDecimal positionAmt = new BigDecimal(pos.get("positionAmt").asText());
                if (positionAmt.compareTo(BigDecimal.ZERO) != 0) {
                    String side = positionAmt.signum() > 0 ? "SELL" : "BUY";

                    LinkedHashMap<String, Object> closeParams = new LinkedHashMap<>();
                    closeParams.put("symbol", symbol);
                    closeParams.put("side", side);
                    closeParams.put("type", "MARKET");
                    closeParams.put("quantity", positionAmt.abs().toPlainString());

                    String closeResult = client.account().newOrder(closeParams);
                    log.info("Closed position {} {} contracts: {}", symbol, positionAmt, closeResult);
                    return;
                }
            }

            log.info("No open position found for {}", symbol);
        } catch (Exception e) {
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
