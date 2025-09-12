package com.andnor.tradenet.core.config;

import com.andnor.tradenet.core.model.SymbolInfo;
import com.andnor.tradenet.domain.telegram.service.impl.TelegramBotServiceImpl;
import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class AppConfig {
    private final BinanceConfigProperties binanceConfigProperties;

    @Bean
    @Profile("prod")
    public UMFuturesClientImpl binanceClient() {
        String apiKey = binanceConfigProperties.getKey();
        String secretKey = binanceConfigProperties.getSecret();
        return new UMFuturesClientImpl(apiKey, secretKey);
    }

    @Bean
    @Profile("dev")
    public UMFuturesClientImpl testBinanceClient() {
        String apiKey = binanceConfigProperties.getKey();
        String secretKey = binanceConfigProperties.getSecret();
        return new UMFuturesClientImpl(apiKey, secretKey, "https://testnet.binancefuture.com");
    }

    @Bean
    public Map<String, SymbolInfo> symbolInfoCache(UMFuturesClientImpl client) {
        log.info("Initializing symbolInfoCache bean...");

        Map<String, SymbolInfo> cache = new ConcurrentHashMap<>();

        try {
            String result = client.market().exchangeInfo();
            JSONObject json = new JSONObject(result);
            JSONArray symbols = json.getJSONArray("symbols");

            for (int i = 0; i < symbols.length(); i++) {
                JSONObject symbolObj = symbols.getJSONObject(i);
                String symbol = symbolObj.getString("symbol");
                JSONArray filters = symbolObj.getJSONArray("filters");

                BigDecimal stepSize = null;
                BigDecimal tickSize = null;

                for (int j = 0; j < filters.length(); j++) {
                    JSONObject filter = filters.getJSONObject(j);
                    String filterType = filter.getString("filterType");

                    if ("LOT_SIZE".equals(filterType)) {
                        stepSize = new BigDecimal(filter.getString("stepSize"));
                    } else if ("PRICE_FILTER".equals(filterType)) {
                        tickSize = new BigDecimal(filter.getString("tickSize"));
                    }
                }

                if (stepSize != null && tickSize != null) {
                    int quantityPrecision = stepSize.stripTrailingZeros().scale();
                    int pricePrecision = tickSize.stripTrailingZeros().scale();

                    cache.put(symbol, SymbolInfo.builder()
                            .quantityPrecision(quantityPrecision)
                            .pricePrecision(pricePrecision)
                            .stepSize(stepSize)
                            .tickSize(tickSize)
                            .build());
                }
            }
            log.info("SymbolInfoCache initialized with {} symbols", cache.size());
        } catch (Exception e) {
            log.error("Failed to initialize symbolInfoCache: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize symbolInfoCache", e);
        }
        return cache;
    }

  @Bean
  public TelegramBotsApi telegramBotsApi(TelegramBotServiceImpl arbitrageBot) throws TelegramApiException {
    TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
    api.registerBot(arbitrageBot);
    return api;
  }

}
