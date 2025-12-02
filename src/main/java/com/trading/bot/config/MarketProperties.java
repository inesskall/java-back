package com.trading.bot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "market.binance")
public class MarketProperties {
    private String streamUrl;
    private String symbol;
}
