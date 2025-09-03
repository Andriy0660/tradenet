package com.andnor.tradenet.domain.exchange.impl;

import com.andnor.tradenet.domain.exchange.ExchangeService;
import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class BinanceService implements ExchangeService {

    private final ObjectMapper mapper;

    @Value("${binance.api.key}")
    private String apiKey;

    @Value("${binance.api.secret}")
    private String secretKey;

    @Value("${binance.futures.leverage:10}")
    private Integer defaultLeverage;

    @Value("${binance.futures.margin-type:ISOLATED}")
    private String marginType;

    @Value("${binance.testnet.enabled:false}")
    private boolean testnetEnabled;

    private UMFuturesClientImpl client;

    @PostConstruct
    public void initialize() {
        try {

            log.info("Initializing Binance production clients");

            // Initialize production clients
            this.client = new UMFuturesClientImpl(apiKey, secretKey, "https://testnet.binancefuture.com");

            log.info("✅ Binance service initialized successfully");

        } catch (Exception e) {
            log.error("❌ Failed to initialize Binance service", e);
            throw new RuntimeException("Binance service initialization failed", e);
        }
    }

    public BigDecimal getCurrentPrice(String symbol) {
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


    public void openLongPosition(String symbol, BigDecimal usdAmount, BigDecimal stopLoss, BigDecimal takeProfit) {
        // 1. Відкрити LONG по маркету
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("symbol", symbol);
        params.put("side", "BUY");
        params.put("type", "MARKET");
        params.put("quoteOrderQty", usdAmount);   // сума в USDT

        String orderResult = client.account().newOrder(params);
        log.info("Opened LONG {} for {} USDT: {}", symbol, usdAmount, orderResult);

        // 2. Стоп-лосс
        if (stopLoss != null) {
            LinkedHashMap<String, Object> slParams = new LinkedHashMap<>();
            slParams.put("symbol", symbol);
            slParams.put("side", "SELL");
            slParams.put("type", "STOP_MARKET");
            slParams.put("stopPrice", stopLoss.toPlainString());
            slParams.put("closePosition", "true"); // закриває всю позицію
            slParams.put("timeInForce", "GTC");
            String slResult = client.account().newOrder(slParams);
            log.info("Placed STOP LOSS at {} for {}: {}", stopLoss, symbol, slResult);
        }

        // 3. Тейк-профіт
        if (takeProfit != null) {
            LinkedHashMap<String, Object> tpParams = new LinkedHashMap<>();
            tpParams.put("symbol", symbol);
            tpParams.put("side", "SELL");
            tpParams.put("type", "TAKE_PROFIT_MARKET");
            tpParams.put("stopPrice", takeProfit.toPlainString());
            tpParams.put("closePosition", "true");
            tpParams.put("timeInForce", "GTC");
            String tpResult = client.account().newOrder(tpParams);
            log.info("Placed TAKE PROFIT at {} for {}: {}", takeProfit, symbol, tpResult);
        }
    }

    /**
     * Відкрити SHORT на суму (USDT) + SL і TP
     */
    public void openShortPosition(String symbol, BigDecimal usdAmount, BigDecimal stopLoss, BigDecimal takeProfit) {
        // 1. Відкрити SHORT по маркету
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("symbol", symbol);
        params.put("side", "SELL");
        params.put("type", "MARKET");
        params.put("quoteOrderQty", usdAmount);

        String orderResult = client.account().newOrder(params);
        log.info("Opened SHORT {} for {} USDT: {}", symbol, usdAmount, orderResult);

        // 2. Стоп-лосс
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

        // 3. Тейк-профіт
        if (takeProfit != null) {
            LinkedHashMap<String, Object> tpParams = new LinkedHashMap<>();
            tpParams.put("symbol", symbol);
            tpParams.put("side", "BUY");
            tpParams.put("type", "TAKE_PROFIT_MARKET");
            tpParams.put("stopPrice", takeProfit.toPlainString());
            tpParams.put("closePosition", "true");
            tpParams.put("timeInForce", "GTC");
            String tpResult = client.account().newOrder(tpParams);
            log.info("Placed TAKE PROFIT at {} for {}: {}", takeProfit, symbol, tpResult);
        }
    }

    /**
     * Закрити повністю відкриту позицію (без вказання кількості)
     */
    public void closePosition(String symbol) {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);

            // 1. Дізнаємось відкриту позицію
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

    public BigDecimal getAccountBalance() {
        String result = client.account().futuresAccountBalance(new LinkedHashMap<>());
        try {
            JsonNode node = mapper.readTree(result);
            // balance повертає масив. Беремо USDT (як приклад).
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
