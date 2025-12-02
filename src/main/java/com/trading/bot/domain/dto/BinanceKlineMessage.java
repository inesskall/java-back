package com.trading.bot.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BinanceKlineMessage {

    private String e;
    private long E;
    private String s;

    @JsonProperty("k")
    private Kline kline;

    @Data
    public static class Kline {
        @JsonProperty("t")
        private long startTime;

        @JsonProperty("T")
        private long closeTime;

        @JsonProperty("s")
        private String symbol;

        @JsonProperty("i")
        private String interval;

        @JsonProperty("o")
        private String openPrice;

        @JsonProperty("c")
        private String closePrice;

        @JsonProperty("h")
        private String highPrice;

        @JsonProperty("l")
        private String lowPrice;

        @JsonProperty("v")
        private String volume;

        @JsonProperty("x")
        private boolean finalBar;
    }
}
