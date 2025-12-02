package com.trading.bot.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketTickDto {
    private String symbol;
    private LocalDateTime timestamp;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;

}
