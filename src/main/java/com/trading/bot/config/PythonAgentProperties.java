package com.trading.bot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "python.agent")
public class PythonAgentProperties {

    private String baseUrl;
    private long timeoutMs = 2000;
}