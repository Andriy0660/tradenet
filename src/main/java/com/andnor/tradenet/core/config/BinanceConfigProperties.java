package com.andnor.tradenet.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "binance.api")
@Getter
@Setter
public class BinanceConfigProperties {
    private String key;
    private String secret;
}
