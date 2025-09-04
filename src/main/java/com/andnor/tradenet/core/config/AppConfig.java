package com.andnor.tradenet.core.config;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@RequiredArgsConstructor
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
}
