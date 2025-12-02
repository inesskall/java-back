package com.trading.bot.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TradeEventDto {
    private String id;
    private String symbol;
    private String side;
    private double price;
    private double volume;
    private double realizedPnl;
    private double balanceAfter;
    private double positionSizeAfter;
    private LocalDateTime timestamp;
    private String reason;
}