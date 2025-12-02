package com.trading.bot.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentDecisionDto {

    private String action;
    private String symbol;
    private Double quantity;
    private Double price;
    private String reason;
    private Double balance;
    private Double equity;
    private Double realizedPnl;
    private Double roiPct;
    private String positionSide;
    private Double positionSize;
    private LocalDateTime positionOpenTime;
    private Double takeProfitPrice;
    private Double stopLossPrice;
    private Double positionNotional;
    private Double avgEntryPrice;
}